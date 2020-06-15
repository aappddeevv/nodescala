// graphql server that illustrates calling into scala
// and having a multi-threaded scala defined resolver
// return a result. The different resolvers take different
// types of resources as inputs but generally do
// not callback into nodejs.

// The example mostly comes from https://www.apollographql.com/docs/apollo-server/getting-started

const { ApolloServer, gql } = require("apollo-server");
const DataLoader = require("dataloader");

const typeDefs = gql`
  type Book {
    id: ID!
    title: String!
    isbn: String!
    description: String
    pages: Int
    authors: [Author!]!
  }
  type Author {
    id: ID!
    name: String!
    dob: Int
    books: [Book]!
  }
  type Query {
    books: [Book!]!
    authors: [Author!]!
    findBookById(id:ID!): Book
    findAuthorById(id:ID!): Author
  }
`;

const books = [
  {
    id: "1",
    title: "Harry Potter and the Chamber of Secrets",
    isbn: "0747555877",
    pages: 100,
    authorIds: ["1"],
  },
  {
    id: "2",
    title: "Jurassic Park",
    isbn: "1856862216",
    pages: 200,
    authorIds: ["2"],
  },
];

const authors = [
  {
    id: "1",
    name: "J.K. Rowling",
    dob: 72,
    bookIds: ["1"]
  },
  {
    id: "2",
    name: "Michael Crichton",
    dob: 80,
    bookIds: ["2"]
  },
];

const resolvers = {
  Query: {
    books: () => books,
    authors: () => authors,
    // could be async or sync
    findBookById: async (parent, { id }, { bookLoader }, info) => bookLoader.load(id),
    findAuthorById: async (parent, { id }, { authorLoader }, info) => authorLoader.load(id),
  },
  // resolvers for books
  Author: {
    books: async ({ bookIds }, args, { bookLoader }, info) => bookLoader.loadMany(bookIds)
  },
  Book: {
    authors: async ({ authorIds }, args, { authorLoader }, info) => authorLoader.loadMany(authorIds),
    description: async ({ id }, argcs, context, info) => {
      return Packages.example5.BookResolvers.description(id, context)
    }
  }
}

// export "database" to other languages through polyglot bindings
Polyglot.export("books", books)
Polyglot.export("authors", authors)

// can call bookLeader.load(title)
function createLoaders() {
  return {
    bookLoader: new DataLoader((keys) => {
      return Promise.resolve(keys.map(id => books.find(book => book.id === id)))
    }),
    authorLoader: new DataLoader((keys) => {
      return Promise.resolve(keys.map(id => authors.find(author => author.id === id)))
    })
  }
}

const server = new ApolloServer({
  typeDefs,
  resolvers,
  context: () => ({
    token: "XYZ",
    ...createLoaders()
  }),
});

server.listen().then(({ url }) => {
  console.log(`🚀  Server ready at ${url}`);
});
