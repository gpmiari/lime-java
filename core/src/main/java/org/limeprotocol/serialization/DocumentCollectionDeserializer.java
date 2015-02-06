package org.limeprotocol.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.limeprotocol.Document;
import org.limeprotocol.DocumentCollection;
import org.limeprotocol.MediaType;
import org.limeprotocol.Session;

import java.io.IOException;
import java.util.Iterator;

public class DocumentCollectionDeserializer extends JsonDeserializer<DocumentCollection> {

    public static final String SCHEME_KEY = "scheme";

    @Override
    public DocumentCollection deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        ObjectCodec oc = jsonParser.getCodec();
        ObjectNode node = (ObjectNode)oc.readTree(jsonParser);

        DocumentCollection collection = new DocumentCollection();

        collection.setTotal(node.with("total").asInt());
        collection.setItemType(MediaType.parse(node.with("itemType").asText()));
        ArrayNode documentsNode = (ArrayNode) node.get("items");

        for (Iterator iterator = documentsNode.elements(); iterator.hasNext();) {
            ObjectNode documentNode = (ObjectNode)iterator.next();
            //Document document = oc.readValue(documentNode, )
        }

        return collection;
    }
}
