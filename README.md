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

Não envia notificações nem acessa calendários. Títulos limitados a 30 caracteres ASCII nesta versão para manter linhas válidas de iCalendar. Compose, Room e alarmes ficam para uma próxima etapa.

A proposta completa de evolução está em `ARQUITETURA.md`. As tecnologias propostas nessa arquitetura não devem ser interpretadas como dependências já implementadas.

## Próximas entregas

- [ ] Cadastrar título e validade — ampliar o núcleo atual até o fluxo completo.
- [ ] Listar próximos vencimentos — ampliar o núcleo atual até o fluxo completo.
- [ ] Exportar lembretes em formato de calendário — ampliar o núcleo atual até o fluxo completo.

Licença: ainda não definida.

## Roteiro de qualidade

Consulte o [guia de validação e manutenção](docs/GUIA_DE_VALIDACAO.md) para reproduzir testes, entender as limitações e revisar mudanças com segurança.


## Consultar próximos vencimentos

```sh
java -jar app.jar --proximos 2026-11-03 30 examples/documentos.tsv
```

Lista documentos que vencem da data informada até 30 dias depois, incluindo os dois extremos. Documentos já vencidos ficam fora da consulta. Use `0` para consultar apenas o dia informado; a janela aceita de 0 a 3650 dias. A saída TSV é ordenada por validade e, em caso de empate, por título. Sem resultados, mantém apenas o cabeçalho.

A consulta não altera o arquivo e não envia notificações. O comando original de exportação iCalendar continua disponível, com seu limite de títulos ASCII. A função `upcoming(documents, today, windowDays)` fornece a mesma consulta para outras interfaces.
