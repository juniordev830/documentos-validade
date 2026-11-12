# Guia técnico — Documentos e Validade

## Estado atual

Ferramenta Kotlin para datas de validade e exportação simples iCalendar, sem notificações automáticas.

## Referências de código

`Main.kt`, `examples/documentos.tsv` e `ARQUITETURA.md`.

## Validar mudanças

Compilar `kotlinc Main.kt -include-runtime -d app.jar`; executar demo/teste conforme instruções do README.

Usar somente dados fictícios e registrar comandos executados com resultados no PR. A mera presença de um teste no repositório não comprova que ele passou.

## Cenários de regressão

Validar data inexistente, caractere de controle em título, saída .ics com escape correto, documento vencido e ordenação.

## Próximo incremento

Alarmes e calendário compartilhado não existem nesta versão. Próximo incremento: teste de importação do ICS em leitores externos.

## Critérios de revisão

- [ ] Código e documentação refletem o mesmo comportamento.
- [ ] Casos de fronteira estão protegidos por teste ou roteiro de validação.
- [ ] Arquivos e saídas geradas ficam fora do versionamento, quando contêm dados privados.
- [ ] Mudanças passam por branch e PR, sem integração automática.
