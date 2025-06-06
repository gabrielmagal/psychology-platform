package br.com.psicologia.service.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Filter {
    private List<FilterParam> filterParams;
    private List<SortingParams> sortingParams;
}
