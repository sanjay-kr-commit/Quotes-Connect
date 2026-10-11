package connects.quotes.graphql

object GraphiqlHtml {
    val content = """
        <!DOCTYPE html>
        <html>
          <head>
            <title>Quotes Connect - GraphQL Gateway</title>
            <link href="https://unpkg.com/graphiql/graphiql.min.css" rel="stylesheet" />
            <style>
              body {
                height: 100vh;
                margin: 0;
                overflow: hidden;
                background-color: #0d1117;
                color: #c9d1d9;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
              }
              #graphiql {
                height: 100vh;
              }
            </style>
          </head>
          <body>
            <div id="graphiql">Loading GraphQL Gateway...</div>
            <script
              crossorigin
              src="https://unpkg.com/react/umd/react.production.min.js"
            ></script>
            <script
              crossorigin
              src="https://unpkg.com/react-dom/umd/react-dom.production.min.js"
            ></script>
            <script
              crossorigin
              src="https://unpkg.com/graphiql/graphiql.min.js"
            ></script>
            <script>
              const fetcher = GraphiQL.createFetcher({
                url: '/graphql',
              });
              ReactDOM.render(
                React.createElement(GraphiQL, {
                  fetcher: fetcher,
                  defaultQuery: `# Quotes Connect - GraphQL Gateway
# 1. Fetch the public Billboard
query GetBillboard {
  billboard(limit: 20) {
    isOrphan
    advertisedAt
    passage {
      id
      type
      content
      tags
      isRecordable
      createdAt
      subscriberIds
      subscribedToIds
    }
  }
}
`,
                }),
                document.getElementById('graphiql'),
              );
            </script>
          </body>
        </html>
    """.trimIndent()
}
