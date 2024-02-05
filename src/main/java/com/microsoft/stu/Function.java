package com.microsoft.stu;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.JSONObject;

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient;
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClientBuilder;
import com.azure.ai.formrecognizer.documentanalysis.models.CurrencyValue;
import com.azure.ai.formrecognizer.documentanalysis.models.AddressValue;
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentFieldType;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.StorageAccount;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {

    private static final String MODEL_ID = "prebuilt-invoice";

    @FunctionName("InvoicesManager")
    @StorageAccount("demoformrecognizer2024")
    public void run(
            @BlobTrigger(name = "content", path = "invoices/{name}", dataType = "binary", source = "EventGrid") byte[] content,
            @BindingName("name") String name,
            @CosmosDBOutput(name = "database", databaseName = "invoices-db", connection = "CosmosDBConnectionString", containerName = "invoices", createIfNotExists = true, partitionKey = "/id") OutputBinding<String> outputItem,
            final ExecutionContext context) {

        context.getLogger().info(
                "Blob trigger function processed a blob.\n Name: " + name + "\n Size: " + content.length + " Bytes");

        // get form recognizer endpoint and key from environment variables
        String frEndpoint = getProperty("FR_ENDPOINT");
        String frKey = getProperty("FR_KEY");

        DocumentAnalysisClient client = new DocumentAnalysisClientBuilder()
                .credential(new AzureKeyCredential(frKey))
                .endpoint(frEndpoint)
                .buildClient();

        JSONObject invoiceJson = initializeJsonObject(name);

        client.beginAnalyzeDocument(MODEL_ID, BinaryData.fromBytes(content))
                .getFinalResult()
                .getDocuments().stream()
                .map(AnalyzedDocument::getFields)
                .forEach(documentFieldMap -> documentFieldMap.forEach((key, documentField) -> {
                    Map<String, Object> field = readDocumentField(key, documentField, new HashMap<String, Object>());
                    // iterate field entries
                    field.forEach((fieldKey, fieldValue) -> {
                        invoiceJson.put(fieldKey, fieldValue);
                    });
                }));

        // save in cosmos db
        outputItem.setValue(invoiceJson.toString());
    }

    private JSONObject initializeJsonObject(String name) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", String.valueOf(Math.abs(new Random().nextInt())));
        jsonObject.put("invoiceFile", name);
        return jsonObject;
    }

    private Map<String, Object> readDocumentField(String key, DocumentField documentField, Map<String, Object> map) {
        System.out.printf("Field text: %s%n", key);
        System.out.printf("Confidence score: %.2f%n", documentField.getConfidence());
        if (documentField.getType() == DocumentFieldType.DATE) {
            LocalDate date = documentField.getValueAsDate();
            System.out.printf("Field value data content: %s%n", date);
            map.put(key, date);
        }
        if (documentField.getType() == DocumentFieldType.STRING) {
            String valueString = documentField.getValueAsString();
            System.out.printf("Field value data content: %s%n", valueString);
            map.put(key, valueString);
        }
        if (documentField.getType() == DocumentFieldType.LONG) {
            Long valueLong = documentField.getValueAsLong();
            System.out.printf("Field value data content: %d%n", valueLong);
            map.put(key, valueLong);
        }
        if (documentField.getType() == DocumentFieldType.DOUBLE) {
            Double valueDouble = documentField.getValueAsDouble();
            System.out.printf("Field value data content: %.2f%n", valueDouble);
            map.put(key, valueDouble);
        }
        if (documentField.getType() == DocumentFieldType.CURRENCY) {
            CurrencyValue value = documentField.getValueAsCurrency();
            String currencyValue = String.format("%.2f%s", value.getAmount(), value.getSymbol());
            System.out.printf("Field value data content: %s%n", currencyValue);
            map.put(key, currencyValue);
        }
        if (documentField.getType() == DocumentFieldType.ADDRESS) {
            AddressValue addressDocumentField = documentField.getValueAsAddress();
            JSONObject addressJson = new JSONObject(addressDocumentField);
            System.out.printf("Field value data content: %s%n", addressJson.toString());
            map.put(key, addressJson);
        }

        if (documentField.getType() == DocumentFieldType.MAP) {
            System.out.println("Field value data content is a map");
            documentField.getValueAsMap().forEach((mapKey, documentFieldEntry) -> {
                Map<String, Object> field = readDocumentField(mapKey, documentFieldEntry,
                    new HashMap<String, Object>());
                map.put(mapKey, field.get(mapKey));
            });
        }
        if (documentField.getType() == DocumentFieldType.LIST) {
            System.out.println("Field value data content is a list");
            List<Object> list = new java.util.ArrayList<Object>();
            for (DocumentField documentFieldEntry : documentField.getValueAsList()) {
                Map<String, Object> field = readDocumentField(key, documentFieldEntry,
                        new HashMap<String, Object>());
                list.add(field);
            }
   
            map.put(key, list);
        }

        return map;
    }

    private String getProperty(String propertyName) {
        String propertyValue = System.getenv(propertyName);
        if (propertyValue == null) {
            throw new IllegalArgumentException("Missing property: " + propertyName);
        }
        return propertyValue;
    }
}
