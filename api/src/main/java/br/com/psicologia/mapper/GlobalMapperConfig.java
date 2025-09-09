package br.com.psicologia.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;

@MapperConfig(
        componentModel = "jakarta",
        injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface GlobalMapperConfig {
}