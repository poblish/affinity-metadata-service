# Architectural and service-level metadata

## Example queries:

Information about a team / stack / service (with tokenising):

    http post https://environment/affinity-meta/graphql query='{ search(query:"expenses") { facets { name, type, extraTags }}}'

Information for a team:

    http post https://environment/affinity-meta/graphql query='{ search(query:"Affinity") { facets { name, type, extraTags }}}'

Infrastructure and its usage:

    http post https://environment/affinity-meta/graphql query='{ search(query:"infra:mysql") { facets { name, extraTags }}}'
    http post https://environment/affinity-meta/graphql query='{ search(query:"infra:rabbitmq") { facets { name, extraTags }}}'

Versions and their usage:

    http post https://environment/affinity-meta/graphql query='{ search(query:"PARENT_POM_VERSION") { facets { name, extraTags }}}'
    http post https://environment/affinity-meta/graphql query='{ search(query:"SPRINGBOOT_VERSION") { facets { name, type, extraTags }}}'
    http post https://environment/affinity-meta/graphql query='{ search(query:"GIT_BRANCH") { facets { name, extraTags }}}'
    http post https://environment-test/affinity-meta/graphql query='{ search(query:"GIT_BRANCH") { facets { name, extraTags }}}'

Rabbit listeners and exchanges, and their usage:

    http post https://environment/affinity-meta/graphql query='{ search(query:"RABBIT_LISTENER_COUNT") { facets { name, extraTags }}}'
    http post https://environment/affinity-meta/graphql query='{ search(query:"RABBIT_EXCHANGE") { facets { name, extraTags }}}'

Cloud Config Profile usage:

    http post https://environment/affinity-meta/graphql query='{ search(query:"security-server") { facets { name, type, extraTags }}}'
    http post https://environment/affinity-meta/graphql query='{ search(query:"security-client") { facets { name, type, extraTags }}}'
