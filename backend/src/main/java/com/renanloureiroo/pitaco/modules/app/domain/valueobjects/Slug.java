package com.renanloureiroo.pitaco.modules.app.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.regex.Pattern;

/**
 * Identificador público e imutável de uma aplicação.
 *
 * <p>
 * Aparece em URL e em chave de acesso, e por isso aceita apenas minúsculas,
 * dígitos e hífen entre termos — nada que precise ser escapado ou que mude de
 * forma dependendo de onde é lido.
 *
 * <p>
 * O texto é validado na construção, então um {@code Slug} em mãos é sempre um
 * slug válido: quem o recebe não precisa checar de novo.
 */
public record Slug(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private static final int MAX_LENGTH = 50;

    private static final String INVALID_CODE = "application.slug_invalid";

    public Slug {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Slug é obrigatório");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainException(
                    ErrorType.VALIDATION,
                    INVALID_CODE,
                    "Slug não pode passar de " + MAX_LENGTH + " caracteres");
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new DomainException(
                    ErrorType.VALIDATION,
                    INVALID_CODE,
                    "Slug aceita apenas minúsculas, dígitos e hífen entre termos");
        }
    }

    public static Slug of(String value) {
        return new Slug(value);
    }

    /** O próprio texto: é assim que o slug aparece em log, URL e mensagem. */
    @Override
    public String toString() {
        return value;
    }
}
