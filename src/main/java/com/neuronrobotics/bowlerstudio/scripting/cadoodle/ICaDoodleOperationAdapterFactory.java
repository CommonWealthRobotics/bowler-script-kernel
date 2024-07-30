package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ICaDoodleOperationAdapterFactory implements TypeAdapterFactory {
    private final Map<String, Class<? extends ICaDoodleOpperation>> typeRegistry = new HashMap<>();
    private final Map<Class<? extends ICaDoodleOpperation>, String> classRegistry = new HashMap<>();

    public ICaDoodleOperationAdapterFactory() {
    	registerType("AddFromFile", AddFromFile.class);
        registerType("AddFromScript", AddFromScript.class);
        registerType("Allign", Allign.class);
        registerType("Delete", Delete.class);
        registerType("Group", Group.class);
        registerType("Hide", Hide.class);
        registerType("Lock", Lock.class);
        registerType("Mirror", Mirror.class);
        registerType("MoveCenter", MoveCenter.class);
        registerType("Paste", Paste.class);
        registerType("Resize", Resize.class);
        registerType("Show", Show.class); 
        registerType("ToHole", ToHole.class);       
        registerType("ToSolid", ToSolid.class);
        registerType("UnGroup", UnGroup.class);
        registerType("UnLock", UnLock.class);
        
    }

    private void registerType(String typeName, Class<? extends ICaDoodleOpperation> clazz) {
        typeRegistry.put(typeName, clazz);
        classRegistry.put(clazz, typeName);
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!ICaDoodleOpperation.class.isAssignableFrom(type.getRawType())) {
            return null;
        }

        final TypeAdapter<JsonElement> jsonElementAdapter = gson.getAdapter(JsonElement.class);

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                JsonObject jsonObject = new JsonObject();
                String typeName = classRegistry.get(value.getClass());
                if (typeName == null) {
                    throw new JsonParseException("Unknown class: " + value.getClass());
                }
                jsonObject.addProperty("type", typeName);
                @SuppressWarnings("unchecked")
                TypeAdapter<T> delegateAdapter = (TypeAdapter<T>) gson.getDelegateAdapter(ICaDoodleOperationAdapterFactory.this, TypeToken.get((Class<T>) value.getClass()));
                JsonElement dataElement = delegateAdapter.toJsonTree(value);
                jsonObject.add("data", dataElement);
                jsonElementAdapter.write(out, jsonObject);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                JsonObject jsonObject = jsonElementAdapter.read(in).getAsJsonObject();
                JsonElement typeElement = jsonObject.get("type");
                JsonElement dataElement = jsonObject.get("data");
                String typeName = typeElement.getAsString();
                Class<? extends ICaDoodleOpperation> clazz = typeRegistry.get(typeName);
                if (clazz == null) {
                    throw new JsonParseException("Unknown type: " + typeName);
                }
                TypeAdapter<? extends ICaDoodleOpperation> delegateAdapter = gson.getDelegateAdapter(ICaDoodleOperationAdapterFactory.this, TypeToken.get(clazz));
                return (T) delegateAdapter.fromJsonTree(dataElement);
            }
        }.nullSafe();
    }


}
