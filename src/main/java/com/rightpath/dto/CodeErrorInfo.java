package com.rightpath.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@AllArgsConstructor
@Data
public class CodeErrorInfo {
    private String type;       // e.g., SyntaxError, CompilationError
    private Integer line;      // Line number
    private String message;    // Short message
    private String fullTrace;  // Full raw output

   
}
