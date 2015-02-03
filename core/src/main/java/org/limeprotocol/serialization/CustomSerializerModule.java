package org.limeprotocol.serialization;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.limeprotocol.Node;

public class CustomSerializerModule extends SimpleModule {

    public CustomSerializerModule() {
        super("CustomSerializers", new Version(1,0,0,null));
        addSerializer(Enum.class, new EnumSerializer());
        addSerializer(new NodeSerializer());
        addDeserializer(Node.class, new NodeDeserializer());
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        Deserializers.Base deser = new Deserializers.Base() {
            @SuppressWarnings("unchecked")
            @Override
            public JsonDeserializer<?> findEnumDeserializer(Class<?> type,
                                                            DeserializationConfig config, BeanDescription beanDesc)
                    throws JsonMappingException {
                return new EnumDeserializer((Class<Enum<?>>) type);
            }
        };
        context.addDeserializers(deser);
    };

}