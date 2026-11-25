# Módulos reaproveitáveis

O que é comum a qualquer sistema (autenticação, auditoria, usuário, pagamento, avaliações,
perfil) mora aqui, separado da aplicação. Cada pasta gera um `.jar` próprio e **nenhum módulo
importa nada da aplicação** (`api/`); a dependência é só da aplicação para os módulos.

| Módulo | O que traz | Depende de |
| --- | --- | --- |
| `platform-core` | Multi-tenant (schema por tenant + Flyway), CRUD genérico e descrição de entidades para formulários, validação de token do Keycloak, auditoria, cifragem de campos, validação de DTO, erros | — |
| `platform-user` | Usuário base extensível, conta no Keycloak (criar, alterar, excluir, senha provisória) e o usuário logado da requisição | `platform-core` |
| `platform-review` | Avaliações: nota, recomendação, comentário com autorização e moderação | `platform-core` |
| `platform-profile` | Perfil público de apresentação | `platform-core` |
| `platform-payment` | Pagamento online (Asaas) atrás de `PaymentGateway`, com o pagável vindo de `PayableSource` | `platform-core`, `platform-user` |

## Compilar

Na raiz do repositório (precisa de JDK 23):

```bash
./mvnw clean install        # módulos + aplicação + testes
./mvnw -f modules/pom.xml install        # só os módulos
```

Depois de um `install`, a aplicação também compila sozinha dentro de `api/`.

## Usar em outro sistema

```xml
<dependency>
  <groupId>br.com.mindhaven.platform</groupId>
  <artifactId>platform-user</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Os `.jar` já trazem o índice Jandex, então o Quarkus encontra os beans, as entidades e as
rotas deles sem configuração extra.

## Estender o usuário

O módulo não define a tabela nem a entidade final de usuário, só a base. Cada sistema declara
a sua e acrescenta o que precisar:

```java
@Entity
@Table(name = "user_info")
@Getter @Setter @NoArgsConstructor @SuperBuilder
public class UserEntity extends BaseUserEntity {
    @Column(name = "crmv")
    private String crmv;                 // campo que só este sistema tem

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType;           // os perfis também são de cada sistema
}

public class UserDto extends BaseUserDto {
    @ILabel("CRMV") @IShowInForm @IGroup("Acesso")
    private String crmv;
}
```

A tabela escolhida precisa ter as colunas da base: `id`, `keycloak_id`,
`registered_by_keycloak_id`, `cpf`, `first_name`, `last_name`, `email`, `phone_number` e
`birth_date`.

O cadastro e a conta no Keycloak são iguais em qualquer sistema e ficam em `UserAccounts`:

```java
// cria a conta e o cadastro; se o banco recusar, a conta é desfeita
UserEntity saved = accounts.create(tenant, user, Set.of("VETERINARIO_ROLE"), quemCadastrou.getKeycloakId(),
        u -> repository.save(tenant, u));
saved.getTemporaryPassword();            // mostrada uma vez a quem cadastrou

accounts.sync(tenant, user, roles);      // depois de alterar nome, e-mail ou perfil
accounts.remove(tenant, user);           // depois de excluir o cadastro
accounts.resetPassword(tenant, user);    // nova senha provisória
```

O que fica com o sistema, de propósito: **quem pode cadastrar quem** e **quais perfis existem**.

Pontos de extensão:

- `BaseUserEntity.accountUsername()`: o login. O padrão é o CPF; sobrescreva para usar e-mail
  ou matrícula.
- `UserLookup`: uma implementação por sistema, dizendo como achar o usuário de uma conta (e o
  que carregar junto, como a foto). O `CurrentUser` usa isso para entregar o usuário logado.
- `PayableSource` (pagamento): o que pode ser pago, quem pode pagar e quem pode receber.

## Publicar no GitHub

Os módulos ainda não são publicados: hoje a aplicação os compila junto, pelo reator da raiz.
Para publicar no GitHub Packages falta acrescentar `distributionManagement` em
`modules/pom.xml` e rodar `./mvnw -f modules/pom.xml deploy` com um token de escrita. Enquanto
a versão for `-SNAPSHOT`, quem consome precisa habilitar snapshots no repositório.

## O que ainda não é do módulo

- As migrações de banco continuam na aplicação (`api/src/main/resources/db/migration`),
  inclusive as tabelas `audit_log`, `review`, `public_profile` e as de pagamento. Um sistema
  novo precisa criar essas tabelas nas próprias migrações.
- Os testes do núcleo rodam junto com os da aplicação (`api/src/test/java/core`).
