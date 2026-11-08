# Documentos e Validade

Ferramenta de linha de comando em Kotlin para cadastrar documentos, acompanhar vencimentos e exportar lembretes para calendários compatíveis com iCalendar (RFC 5545).

## Funcionalidades

- Cadastro, alteração e remoção de documentos em arquivo TSV, com gravação temporária e substituição atômica quando suportada.
- Importação compatível com o formato original de duas colunas (titulo/validade).
- Formato estendido com categoria, notas e antecedência do lembrete.
- Listagem ordenada com dias restantes e situação: VENCIDO, VENCE_HOJE, PROXIMO e EM_DIA.
- Relatório por categoria e por situação, com janela configurável de vencimentos próximos.
- Calendário .ics com identificadores estáveis, escape de texto, CRLF, dobra de linhas UTF-8 e alarmes opcionais.
- Validação de datas, tamanho de campos, caracteres de controle, duplicações e limite de arquivo.

## Requisitos e compilação

JDK 17+ e compilador Kotlin 2.1.0 (ou compatível). Na raiz do projeto:

~~~sh
kotlinc Main.kt Tests.kt -include-runtime -d app.jar
java -jar app.jar --self-test
~~~

A suíte automatizada executa **90 verificações** de validação, importação, persistência, comandos, classificações, relatório e iCalendar. O workflow em .github/workflows/ci.yml também valida os comandos reais contra examples/documentos.tsv.

## Comandos

~~~sh
# Mantém o comando original da primeira versão
java -jar app.jar 2026-11-08 examples/documentos.tsv > validade.ics

# Exportação explícita
java -jar app.jar calendario 2026-11-08 examples/documentos.tsv > validade.ics

# Consultar vencimentos e relatórios (janela padrão: 30 dias)
java -jar app.jar listar 2026-11-08 examples/documentos.tsv
java -jar app.jar listar 2026-11-08 examples/documentos.tsv --dias 60
java -jar app.jar relatorio 2026-11-08 examples/documentos.tsv

# Criar ou editar um arquivo TSV local
java -jar app.jar adicionar meus-documentos.tsv "CNH" 2027-01-15 "Pessoal" "Renovar" 30
java -jar app.jar atualizar meus-documentos.tsv "CNH" 2027-01-15 "CNH" 2027-02-01 "Pessoal" "Renovada" 30
java -jar app.jar remover meus-documentos.tsv "CNH" 2027-02-01
~~~

**Importante:** os comandos de edição modificam o TSV informado. Faça backup dos dados reais antes de editar. As datas são ISO 8601 (AAAA-MM-DD). Os argumentos de texto com espaços devem ser colocados entre aspas.

## Formato de arquivo

Formato legado:

~~~tsv
titulo	validade
CNH	2027-01-15
~~~

Formato estendido:

~~~tsv
titulo	validade	categoria	notas	lembrete_dias
CNH	2027-01-15	Pessoal	Renovar	30
~~~

Títulos são únicos por combinação de nome (ignorando maiúsculas/minúsculas) e validade. Caracteres de tabulação e quebras de linha são rejeitados nos campos de texto. Limites: título 80 caracteres, categoria 40, notas 200, antecedência 0–365 dias e até 10.000 registros.

## Organização

- Main.kt: regras de negócio, leitura/gravação TSV, CLI e geração iCalendar.
- Tests.kt: testes de regressão sem dependências adicionais.
- examples/documentos.tsv: dados fictícios para experimentar os comandos.
- .github/workflows/ci.yml: compilação, testes e validação de integração.

Não envia notificações, não acessa calendários e não transmite dados pela rede. O calendário exportado pode ser importado manualmente no aplicativo de preferência. Consulte ARQUITETURA.md para ideias futuras, não necessariamente implementadas.

Licença: ainda não definida.
