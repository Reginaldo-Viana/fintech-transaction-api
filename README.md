# Fintech Transaction API - PIX & Wallet

Core bancário simplificado em Java 21 + Spring Boot 3. Demonstra uma API financeira com contas, depósitos, transferências atômicas, extrato, idempotência e autenticação JWT.

## Features

- Criação de contas com CPF e e-mail únicos
- Depósitos e transferências com valores decimais e validação de saldo
- Proteção contra saldo negativo (`422 Unprocessable Entity`)
- Transferências atômicas com bloqueio pessimista das contas
- Idempotência por `Idempotency-Key` no header ou `idempotencyKey` no corpo
- Extrato de depósitos e transferências por conta
- JWT para autenticação e BCrypt para armazenamento de senhas
- PostgreSQL 16, migrations Flyway e testes com Testcontainers

## Tech stack

Java 21, Spring Boot 3, Spring Security, Spring Data JPA, PostgreSQL 16, Flyway, JJWT, JUnit 5, REST Assured, Testcontainers e Docker Compose.

## Como rodar

Pré-requisitos: Java 21, Maven 3.9+ e Docker com Docker Compose.

```bash
docker compose -f docker-compose.yml up --build
```

A API estará em `http://localhost:8080`. Para rodar a aplicação fora do Compose:

```bash
docker compose -f docker-compose.yml up -d postgres
mvn spring-boot:run
```

Configure `JWT_SECRET` com uma chave Base64 aleatória de pelo menos 256 bits e altere as credenciais do banco em qualquer ambiente que não seja desenvolvimento local.

## Endpoints

| Método | Caminho | Autenticação | Descrição |
|---|---|---|---|
| POST | `/api/v1/accounts` | Não | Cria conta com saldo zero, CPF, e-mail e senha |
| GET | `/api/v1/accounts/{id}` | Bearer JWT | Consulta a própria conta e o saldo |
| POST | `/api/v1/accounts/{id}/deposit` | Bearer JWT | Deposita na própria conta |
| GET | `/api/v1/accounts/{id}/statement` | Bearer JWT | Extrato paginado de depósitos e transferências |
| POST | `/api/v1/transfers` | Bearer JWT | Transfere entre contas |
| POST | `/api/v1/auth/login` | Não | Autentica e retorna JWT |

Os e-mails são usados como identidade do JWT. O usuário autenticado só pode consultar e movimentar a própria conta de origem.

### Criar uma conta

```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"name":"Ana Silva","email":"ana@example.com","document":"529.982.247-25","password":"senha-segura"}'
```

### Obter o token

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","password":"senha-segura"}'
```

### Depositar

```bash
curl -X POST http://localhost:8080/api/v1/accounts/1/deposit \
  -H "Authorization: Bearer SEU_JWT" \
  -H "Idempotency-Key: deposito-2026-0001" \
  -H "Content-Type: application/json" \
  -d '{"amount":100.00}'
```

### Transferir

```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer SEU_JWT" \
  -H "Idempotency-Key: 123e4567-e89b-1234" \
  -H "Content-Type: application/json" \
  -d '{"from":1,"to":2,"amount":100.00,"idempotencyKey":"123e4567-e89b-1234"}'
```

O campo `from` deve identificar a conta associada ao token. Uma repetição com a mesma chave e os mesmos dados retorna a transação existente; a mesma chave com dados diferentes retorna `409 Conflict`.

### Extrato

```bash
curl "http://localhost:8080/api/v1/accounts/1/statement?page=0&size=20" \
  -H "Authorization: Bearer SEU_JWT"
```

## Testes

```bash
mvn test
```

Os testes de integração iniciam PostgreSQL 16 com Testcontainers e precisam de Docker. O GitHub Actions executa a suíte em pushes e pull requests.
# fintech-transaction-api
