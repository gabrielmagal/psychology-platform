package core.controller.dto;

import jakarta.json.bind.annotation.JsonbProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BaseDto {
    @JsonbProperty("id")
    private UUID id;
}
