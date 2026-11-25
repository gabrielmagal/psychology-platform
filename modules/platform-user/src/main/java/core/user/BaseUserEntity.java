package core.user;

import core.repository.model.BaseEntity;
import core.user.keycloak.KeycloakAccount;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * Usuário base: o que todo sistema precisa saber de uma pessoa com acesso — quem é, como
 * falar com ela e qual é a conta dela no provedor de identidade.
 *
 * <p>Não é uma entidade: cada aplicação declara a sua, estendendo esta classe, e escolhe a
 * tabela e os campos próprios (registro profissional, endereço, perfil de acesso...).
 *
 * <pre>{@code
 * @Entity
 * @Table(name = "user_info")
 * @SuperBuilder
 * @NoArgsConstructor
 * public class UserEntity extends BaseUserEntity {
 *     @Column(name = "crmv")
 *     private String crmv;          // campo que só este sistema tem
 * }
 * }</pre>
 *
 * <p>O cadastro e a conta no Keycloak são os mesmos em qualquer aplicação: ver
 * {@link UserAccounts}. As colunas abaixo precisam existir na tabela escolhida.
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class BaseUserEntity extends BaseEntity {
    /** Conta de quem fez o cadastro desta pessoa. */
    @Column(name = "registered_by_keycloak_id")
    private String registeredByKeycloakId;

    /** Identificador da conta no provedor de identidade. */
    @Column(name = "keycloak_id", unique = true)
    private String keycloakId;

    @Column(name = "cpf", nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Email
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /** Foto em base64. Não é coluna: cada aplicação decide onde guardar a imagem. */
    @Transient
    private String profileImage;

    /**
     * Senha provisória recém-gerada. Nunca é gravada: existe só na resposta de quem acabou de
     * cadastrar o usuário (ou de pedir uma senha nova), para ser entregue à pessoa.
     */
    @Transient
    private String temporaryPassword;

    /**
     * Com o que a pessoa entra no sistema. O padrão é o CPF; a aplicação que usa outro login
     * (e-mail, matrícula) sobrescreve este método.
     */
    public String accountUsername() {
        return cpf;
    }

    /** Os dados que vão para a conta no provedor de identidade. */
    public KeycloakAccount toAccount() {
        return new KeycloakAccount(accountUsername(), email, firstName, lastName);
    }

    public String fullName() {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }
}
