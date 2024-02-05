# Document Intelligence Azure function

This sample demonstrates how to use the Document Intelligence service to extract information from invoices and store the results in a Cosmos DB.

The invoice is uploaded to a blob storage and the Azure function is triggered by the blob creation event. The function extracts the information from the invoice and stores it in a Cosmos DB.

## Resources

https://learn.microsoft.com/en-us/azure/ai-services/document-intelligence/how-to-guides/use-sdk-rest-api?view=doc-intel-4.0.0&tabs=linux&pivots=programming-language-java

## Environment 

```bash
export RG=doc-intelligence-demo-rg
export STORAGE_ACCOUNT_NAME=<storage-account-name>
export CONTAINER_NAME=invoices
export FUNCTION_APP_NAME=invoices-manager
export DOC_INTELLIGENCE_NAME=doc-int-service
export COSMOS_DB_NAME=<cosmos-db>
export LOCATION=<location>
```

## Create RG

```bash
az group create --name $RG --location $LOCATION
```

## Create invoices container 

```bash
az storage account create --name $STORAGE_ACCOUNT_NAME --resource-group $RG --location $LOCATION --sku "Standard_LRS"  
az storage container create --name $CONTAINER_NAME --account-name $STORAGE_ACCOUNT_NAME 
```

## Create Doc Intelligence

```bash
az cognitiveservices account create --name $DOC_INTELLIGENCE_NAME --kind FormRecognizer --sku F0 --location $LOCATION --resource-group $RG --yes
```

## Create Cosmos DB

Create Cosmos DB account:

```bash
az cosmosdb create --name $COSMOS_DB_NAME --resource-group $RG --locations regionName=$LOCATION failoverPriority=0 isZoneRedundant=False --default-consistency-level Eventual 
```

Create database:

```bash
az cosmosdb sql database create --account-name $COSMOS_DB_NAME --name invoices-db --resource-group $RG
```

## Deploy application 

```bash
mvn clean package azure-functions:deploy -DfunctionAppName=$FUNCTION_APP_NAME -DresourceGroup=$RG -Dlocation=$LOCATION
```

## Configure application

```bash
# Storage account
export STORAGE_ACCOUNT_FOR_BLOB_CS=$(az storage account show-connection-string --name $STORAGE_ACCOUNT_NAME --resource-group $RG --output tsv | cut -d ';' -f 1-4)
az functionapp config appsettings set --name $FUNCTION_APP_NAME --resource-group $RG --settings "AzureWebJobs"$STORAGE_ACCOUNT_NAME"="$STORAGE_ACCOUNT_FOR_BLOB_CS
# CosmosDB
export COSMOSDB_CONNECTION_STRING=$(az cosmosdb keys list --name $COSMOS_DB_NAME --resource-group $RG --type connection-strings --query connectionStrings[0].connectionString --output tsv)
az functionapp config appsettings set --name $FUNCTION_APP_NAME --resource-group $RG --settings "CosmosDBConnectionString"="$COSMOSDB_CONNECTION_STRING"
# Form Recognizer
export FORM_RECOGNIZER_ENDPOINT=$(az cognitiveservices account show --name $DOC_INTELLIGENCE_NAME --resource-group $RG --query properties.endpoints.FormRecognizer --output tsv)
az functionapp config appsettings set --name $FUNCTION_APP_NAME --resource-group $RG --settings "FR_ENDPOINT"="$FORM_RECOGNIZER_ENDPOINT"
export FORM_RECOGNIZER_KEY=$(az cognitiveservices account keys list --name $DOC_INTELLIGENCE_NAME --resource-group $RG --query key1 --output tsv)
az functionapp config appsettings set --name $FUNCTION_APP_NAME --resource-group $RG --settings "FR_KEY"="$FORM_RECOGNIZER_KEY"
```

## Create subcription for Blob Trigger

Create subscription for blob trigger:

https://learn.microsoft.com/en-us/azure/azure-functions/functions-event-grid-blob-trigger?tabs=isolated-process%2Cnodejs-v4&pivots=programming-language-java#create-the-event-subscription

## Test the application

### Upload invoice

```bash
az storage blob upload --account-name $STORAGE_ACCOUNT_NAME --container-name $CONTAINER_NAME --file ./invoices/invoice.pdf
```

### Check the result

Go to the Azure portal and check the result in the Cosmos DB containter 'invoices' within the database 'invoices-db'.

### Delete invoice after test

```bash
az storage blob delete --account-name $STORAGE_ACCOUNT_NAME --container-name $CONTAINER_NAME --name invoice.pdf
```