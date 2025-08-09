package org.acme;

import io.quarkus.redis.datasource.codecs.Codec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;

@ApplicationScoped
public class PaymentRequestCodec implements Codec {

    private final Jsonb jsonb;

    public PaymentRequestCodec(Jsonb jsonb) {
        this.jsonb = jsonb;
    }

    @Override
    public boolean canHandle(Type clazz) {
        return PaymentRequest.class.equals(clazz);
    }

    @Override
    public byte[] encode(Object target) {
        if (canHandle(target.getClass())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            jsonb.toJson(target, out);
            return out.toByteArray();
        }
        return new byte[0];
    }

    @Override
    public Object decode(byte[] item) {
        if (item == null || item.length == 0) {
            return null;
        }
        return jsonb.fromJson(new ByteArrayInputStream(item), PaymentRequest.class);
    }
}
