# Arquitetura proposta — Documentos e Validade

## Objetivo

Organização de datas de vencimento com exportação para calendário.

## Escolha técnica

Organização móvel de vencimentos e exportação de eventos de calendário.

Stack proposta: Kotlin, Jetpack Compose, Room e iCalendar.

## Conceitos de domínio a detalhar

- Documento
- Vencimento
- Lembrete

## Responsabilidades

| Camada | Responsabilidade |
| --- | --- |
| Entrada e interface | Receber dados, mostrar validações e apresentar o resultado |
| Casos de uso | Coordenar cada operação do escopo inicial |
| Domínio | Definir regras, invariantes e transformações sem depender da interface |
| Adaptadores | Isolar arquivos, persistência e serviços externos quando forem adicionados |

A estrutura de código será ajustada às convenções de Kotlin; estas camadas descrevem responsabilidades e não impõem uma quantidade fixa de pastas.

## Entregas por etapas

1. Escrever um cenário de uso com entrada e resultado esperado.
2. Implementar a menor operação útil e tratar entrada inválida.
3. Concluir os demais itens do escopo inicial.
4. Adicionar persistência ou integração somente onde o fluxo precisar.
5. Registrar instalação, execução, validação e limitações.

## Escopo funcional

- [ ] Cadastrar título e validade
- [ ] Listar próximos vencimentos
- [ ] Exportar lembretes em formato de calendário

## Dados e integrações

Não há serviços externos conectados nem dados reais neste pacote. Exemplos devem ser fictícios. Projetos que consultarem fontes públicas devem registrar a fonte, a data e as condições de uso junto dos dados importados.

## Estado de validação

A especificação foi preparada. Compilação e testes da aplicação ainda não se aplicam porque o código não foi implementado.
