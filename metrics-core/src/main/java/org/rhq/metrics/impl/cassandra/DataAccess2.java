package org.rhq.metrics.impl.cassandra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TupleType;
import com.datastax.driver.core.TupleValue;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.UserType;

import org.rhq.metrics.core.AggregationTemplate;
import org.rhq.metrics.core.Interval;
import org.rhq.metrics.core.MetricType;
import org.rhq.metrics.core.RetentionSettings;
import org.rhq.metrics.core.Tenant;

/**
 * This class will eventually supplant the existing DataAccess class.
 *
 * @author John Sanda
 */
public class DataAccess2 {

    private Session session;

    private PreparedStatement insertTenant;

    private PreparedStatement findTenants;

    public DataAccess2(Session session) {
        this.session = session;
        initPreparedStatements();
    }

    private void initPreparedStatements() {
        insertTenant = session.prepare("INSERT INTO tenants (id, retentions, aggregation_templates) VALUES (?, ?, ?)");
        findTenants = session.prepare("SELECT id, retentions, aggregation_templates FROM tenants");
    }

    public ResultSetFuture insertTenant(Tenant tenant) {
        UserType aggregationTemplateType = session.getCluster().getMetadata().getKeyspace("rhq")
            .getUserType("aggregation_template");
        List<UDTValue> templateValues = new ArrayList<>(tenant.getAggregationTemplates().size());
        for (AggregationTemplate template : tenant.getAggregationTemplates()) {
            UDTValue value = aggregationTemplateType.newValue();
            value.setString("type", template.getType().getCode());

            TupleType intervalType = TupleType.of(DataType.cint(), DataType.text());
            TupleValue tuple = intervalType.newValue();
            tuple.setInt(0, template.getInterval().getLength());
            tuple.setString(1, template.getInterval().getUnits().getCode());
            value.setTupleValue("interval", tuple);
            value.setSet("fns", template.getFunctions());
            templateValues.add(value);
        }

        Map<TupleValue, Integer> retentions = new HashMap<>();
        for (RetentionSettings.RetentionKey key : tenant.getRetentionSettings().keySet()) {
            TupleType metricType = TupleType.of(DataType.text(), DataType.cint(), DataType.text());
            TupleValue tuple = metricType.newValue();
            tuple.setString(0, key.metricType.getCode());
            if (key.interval != null) {
                tuple.setInt(1, key.interval.getLength());
                tuple.setString(2, key.interval.getUnits().getCode());
            }
            retentions.put(tuple, tenant.getRetentionSettings().get(key));
        }

        return session.executeAsync(insertTenant.bind(tenant.getId(), retentions, templateValues));
    }

    public Set<Tenant> findTenants() {
        ResultSet resultSet = session.execute(findTenants.bind());
        Set<Tenant> tenants = new HashSet<>();
        for (Row row : resultSet) {
            Tenant tenant = new Tenant();
            tenant.setId(row.getString(0));

            Map<TupleValue, Integer> retentions = row.getMap(1, TupleValue.class, Integer.class);
            for (Map.Entry<TupleValue, Integer> entry : retentions.entrySet()) {
                MetricType metricType = MetricType.fromCode(entry.getKey().getString(0));
                if (entry.getKey().isNull(1)) {
                    tenant.setRetention(metricType, entry.getValue());
                } else {
                    int length = entry.getKey().getInt(1);
                    Interval.Units units = Interval.Units.fromCode(entry.getKey().getString(2));
                    Interval interval = new Interval(length, units);
                    tenant.setRetention(metricType, interval, entry.getValue());
                }
            }

            List<UDTValue> templateValues = row.getList(2, UDTValue.class);
            for (UDTValue value : templateValues) {
                tenant.addAggregationTemplate(new AggregationTemplate()
                    .setType(MetricType.fromCode(value.getString("type")))
                    .setInterval(toInterval(value.getTupleValue("interval")))
                    .setFunctions(value.getSet("fns", String.class)));
            }

            tenants.add(tenant);
        }
        return tenants;
    }

    private Interval toInterval(TupleValue tuple) {
        return new Interval(tuple.getInt(0), Interval.Units.fromCode(tuple.getString(1)));
    }

}