package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotController;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotLimb;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.MakeRobot;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.ModifyLimb;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CaDoodleJsonOperationAdapterFactory implements TypeAdapterFactory {
	private final Map<String, Class<? extends CaDoodleOperation>> typeRegistry = new HashMap<>();
	private final Map<Class<? extends CaDoodleOperation>, String> classRegistry = new HashMap<>();

	public CaDoodleJsonOperationAdapterFactory() {
		registerType( AddFromFile.class);
		registerType( AddFromScript.class);
		registerType( AddRobotController.class);
		registerType( AddRobotLimb.class);
		registerType( Allign.class);
		registerType( Delete.class);
		registerType( Group.class);
		registerType(Hide.class);
		registerType(Lock.class);
		registerType( MakeRobot.class);
		registerType( Mirror.class);
		registerType( ModifyLimb.class);
		registerType( MoveCenter.class);
		registerType( Paste.class);
		registerType( Resize.class);
		registerType( Show.class);
		registerType(ToHole.class);
		registerType( ToSolid.class);
		registerType( UnGroup.class);
		registerType( UnLock.class);
		registerType( Sweep.class);

	}

	private void registerType(Class<? extends CaDoodleOperation> clazz) {
		String typeName=clazz.getSimpleName();
		typeRegistry.put(typeName, clazz);
		classRegistry.put(clazz, typeName);
	}

	@Override
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if (!CaDoodleOperation.class.isAssignableFrom(type.getRawType())) {
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
				TypeAdapter<T> delegateAdapter = (TypeAdapter<T>) gson.getDelegateAdapter(
						CaDoodleJsonOperationAdapterFactory.this, TypeToken.get((Class<T>) value.getClass()));
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
				Class<? extends CaDoodleOperation> clazz = typeRegistry.get(typeName);
				if (clazz == null) {
					throw new JsonParseException("Unknown type: " + typeName);
				}
				TypeAdapter<? extends CaDoodleOperation> delegateAdapter = gson
						.getDelegateAdapter(CaDoodleJsonOperationAdapterFactory.this, TypeToken.get(clazz));
				com.neuronrobotics.sdk.common.Log.error("JSON Parsing " + typeName);
				return (T) delegateAdapter.fromJsonTree(dataElement);
			}
		}.nullSafe();
	}

}
