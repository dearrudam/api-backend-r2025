package org.acme;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.eclipse.jnosql.databases.mongodb.mapping.MongoDBTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@ApplicationScoped
public class PaymentService {

    private final MongoDBTemplate template;

    @Inject
    public PaymentService(MongoDBTemplate template) {
        this.template = template;
    }

    public Payment savePayment(Payment payment) {
        return template.insert(payment);
    }

    public Map<String, Map<String, Object>> getSummary(Instant from, Instant to) {
        Bson match = null;
        if (from == null || to == null) {
            match = new BsonDocument();
        } else {
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("The 'from' date must be before the 'to' date.");
            }
            match = Aggregates.match(
                    Filters.and(
                            Filters.gte("requestedAt", from.toString()),
                            Filters.lte("requestedAt", to.toString())
                    )
            );
        }
        Bson group = Aggregates.group(
                "$type",
                Accumulators.sum("totalAmount", "$amount"),
                Accumulators.sum("totalRequests", 1)
        );
        Bson[] predicates = {
                match,
                group
        };

        Stream<Map<String, BsonValue>> result = template.aggregate("Payment",
                predicates);

        Map<String, Map<String, Object>> summary = new HashMap<>();

        result.forEach(document -> {
            String type = document.get("_id").asString().getValue();
            Map<String, Object> details = new HashMap<>();
            details.put("totalAmount", document.get("totalAmount").asDecimal128().doubleValue());
            details.put("totalRequests", document.get("totalRequests").asNumber().longValue());
            summary.put(type.toLowerCase(), details);
        });
        Arrays.stream(Payment.PaymentType.values())
                .forEach(type ->
                        summary.computeIfAbsent(
                                type.name().toLowerCase(), k -> {
                                    Map<String, Object> details = new HashMap<>();
                                    details.put("totalAmount", 0.0);
                                    details.put("totalRequests", 0L);
                                    return details;
                                }));
        return summary;
    }
}
