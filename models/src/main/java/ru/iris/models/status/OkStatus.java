package ru.iris.models.status;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class OkStatus extends BackendAnswer {
    private String text;

    public String getStatus() {
        return "OK";
    }
}
