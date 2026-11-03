# Documentos e Validade

Organização de datas de vencimento com exportação para calendário.

## Implementado na primeira versão

**Kotlin — núcleo executável de domínio.** Converte datas de validade em eventos de dia inteiro no formato iCalendar, com UID determinístico, escape de texto e finais CRLF. Opcionalmente inclui um alerta `VALARM` em cada evento.

## Como executar

Instale JDK 17+ e o compilador Kotlin 2.1.0 (ou compatível). Os comandos abaixo partem da raiz do repositório.

```sh
kotlinc Main.kt -include-runtime -d app.jar
java -jar app.jar 2026-11-03 examples/documentos.tsv > validade.ics
java -jar app.jar 2026-11-03 examples/documentos.tsv --alerta-dias 7 > validade-com-alerta.ics
```

## Alertas opcionais no calendário

A opção `--alerta-dias N` aceita valores inteiros de **0 a 365** e inclui um alarme de exibição `VALARM` em cada evento, relativo à sua data inicial. Por exemplo, `--alerta-dias 7` solicita um aviso sete dias antes e `0` solicita aviso no dia do vencimento. Se a opção não for informada, a exportação permanece sem alertas, como na versão anterior.

Os alarmes **não são notificações enviadas pelo programa**: precisam ser importados em um aplicativo de calendário compatível. A exibição e as permissões de notificação dependem do aplicativo e de sua configuração. Os eventos continuam sendo datas de dia inteiro e não contêm informações pessoais além do título fornecido no arquivo TSV.

## Testes

```sh
kotlinc Main.kt -include-runtime -d app.jar
java -jar app.jar --self-test
```

Os testes cobrem invariantes e entradas inválidas. A preparação local não dispõe deste runtime; o workflow em `.github/workflows/ci.yml` executa a validação no GitHub Actions. O resultado de cada execução está na aba Actions, associado ao commit.

## Organização e limites

Código e exemplos: `Main.kt e examples/documentos.tsv`. Os dados de exemplo são fictícios. As funções de domínio são separadas da leitura de argumentos no mesmo arquivo para manter a primeira versão pequena e revisável.

Não envia notificações diretamente nem acessa calendários; apenas gera um arquivo `.ics` com alarmes opcionais. Títulos limitados a 30 caracteres ASCII nesta versão para manter linhas válidas de iCalendar. Compose, Room e alarmes ficam para uma próxima etapa.

A proposta completa de evolução está em `ARQUITETURA.md`. As tecnologias propostas nessa arquitetura não devem ser interpretadas como dependências já implementadas.

## Próximas entregas

- [ ] Cadastrar título e validade — ampliar o núcleo atual até o fluxo completo.
- [ ] Listar próximos vencimentos — ampliar o núcleo atual até o fluxo completo.
- [x] Exportar eventos com alarmes opcionais de antecedência em iCalendar.
- [ ] Integração autenticada com serviços de calendário, com consentimento do usuário.

Licença: ainda não definida.
