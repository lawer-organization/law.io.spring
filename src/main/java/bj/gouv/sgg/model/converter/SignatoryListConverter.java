package bj.gouv.sgg.model.converter;

import bj.gouv.sgg.model.Signatory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.List;

/**
 * JPA converter pour stocker List<Signatory> en JSON en base de données
 */
@Converter
public class SignatoryListConverter implements AttributeConverter<List<Signatory>, String> {
    
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, (com.google.gson.JsonSerializer<LocalDate>) (src, typeOfSrc, context) -> 
                src == null ? null : new com.google.gson.JsonPrimitive(src.toString()))
            .registerTypeAdapter(LocalDate.class, (com.google.gson.JsonDeserializer<LocalDate>) (json, typeOfT, context) -> 
                json == null ? null : LocalDate.parse(json.getAsString()))
            .create();
    private final Type listType = new TypeToken<List<Signatory>>(){}.getType();

    @Override
    public String convertToDatabaseColumn(List<Signatory> signatories) {
        if (signatories == null || signatories.isEmpty()) {
            return null;
        }
        return gson.toJson(signatories);
    }

    @Override
    public List<Signatory> convertToEntityAttribute(String json) {
        if (json == null || json.trim().isEmpty()) {
            return List.of();
        }
        return gson.fromJson(json, listType);
    }
}
