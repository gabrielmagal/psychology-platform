package br.com.mindhaven.adapter.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientSecretDto {
    public String client;
    public String secret;
}
