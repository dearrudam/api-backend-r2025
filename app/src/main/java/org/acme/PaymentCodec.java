package org.acme;

import io.quarkus.redis.datasource.codecs.Codec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;

@ApplicationScoped
public class PaymentCodec implements Codec {

    private final Jsonb jsonb;

    public PaymentCodec(Jsonb jsonb) {
        this.jsonb = jsonb;
    }

    @Override
    public boolean canHandle(Type clazz) {
        return Payment.class.equals(clazz);
    }

    @Override
    public byte[] encode(Object target) {
        if (target instanceof Payment item) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            jsonb.toJson(item, out);
            return out.toByteArray();
        }
        return new byte[0];
    }

    @Override
    public Object decode(byte[] item) {
        if (item == null || item.length == 0) {
            return null;
        }
        return jsonb.fromJson(new ByteArrayInputStream(item), Payment.class);
    }
}
