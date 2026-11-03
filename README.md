# Documentos e Validade

Organização de datas de vencimento com exportação para calendário.

## Implementado na primeira versão

**Kotlin — núcleo executável de domínio.** Converte datas de validade em eventos de dia inteiro no formato iCalendar, com UID determinístico, escape de texto e finais CRLF.

## Como executar

Instale JDK 17+ e o compilador Kotlin 2.1.0 (ou compatível). Os comandos abaixo partem da raiz do repositório.

```sh
kotlinc Main.kt -include-runtime -d app.jar
java -jar app.jar 2026-11-03 examples/documentos.tsv > validade.ics
```

## Testes

```sh
kotlinc Main.kt -include-runtime -d app.jar
java -jar app.jar --self-test
```

Os testes cobrem invariantes e entradas inválidas. A preparação local não dispõe deste runtime; o workflow em `.github/workflows/ci.yml` executa a validação no GitHub Actions. O resultado de cada execução está na aba Actions, associado ao commit.

## Organização e limites

Código e exemplos: `Main.kt e examples/documentos.tsv`. Os dados de exemplo são fictícios. As funções de domínio são separadas da leitura de argumentos no mesmo arquivo para manter a primeira versão pequena e revisável.

Não envia notificações nem acessa calendários. Títulos limitados a 40 caracteres ASCII nesta versão para manter linhas válidas de iCalendar. Compose, Room e alarmes ficam para uma próxima etapa.

A proposta completa de evolução está em `ARQUITETURA.md`. As tecnologias propostas nessa arquitetura não devem ser interpretadas como dependências já implementadas.

## Próximas entregas

- [ ] Cadastrar título e validade — ampliar o núcleo atual até o fluxo completo.
- [ ] Listar próximos vencimentos — ampliar o núcleo atual até o fluxo completo.
- [ ] Exportar lembretes em formato de calendário — ampliar o núcleo atual até o fluxo completo.

Licença: ainda não definida.
