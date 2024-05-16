# Queue Underflow Internal API Usage
## Documentation of Endpoints
Endpoints always start with /api

### Signup
`/signup`

Used to create a new account. Sends an email containing a verification url to the specified email address. Hash needs to be the entered password hashed once with SHA-512.

Takes:
```json
{
  "username": "<username>",
  "email": "<valid email>",
  "password": "<sha512 hash>"
}
```
### Verify
`/verify?code=<code>`

Used to verify an email address. Code is automatically inserted into the url, which is then sent in the email.

### Login
`/login`

Used to log in to an account with the specified credentials. Hash needs to be the entered password hashed once with SHA-512.

Takes:
```json
{
  "username": "<username>",
  "password": "<sha512 hash>"
}
```

### Logout
`/logout`

Used to log out of the currently active account. If no user is present, the following error will be returned: `400: Not logged in.`

### Delete Account
`/delete_account`

Used to delete the currently active account. [WARNING: No confirmation will be taken, this will maybe change in the future]

### Post
`/post`

Used to create a new ticket with the currently active account. If no user is present, the following error will be returned: `400: Not logged in.`

Tags:
- 2 - Problem
- 4 - Question

Takes:
```json
{
  "title": "<title>",
  "body": "<body>",
  "tags": [<tags>]
}
```
### Comment
`/comment`

Used to comment on a ticket or comment with the currently active account. If no user is present, the following error will be returned: `400: Not logged in.`

Takes:
```json
{
  "id": <object id (Long)>,
  "body": "<body>"
}
```

### Delete
`/delete`

Used to delete a ticket or comment with the currently active account. Operation will succeed if the user is the ticket/comment owner, or if he has admin permissions. If no user is present, the following error will be returned: `400: Not logged in.`

Takes:
```json
{
  "id": <object id (Long)>
}
```

### Tickets
`/tickets?limit=<limit>&offset=<offset>&nocom`

Returns:
```json
[<tickets>]
```

Used to receive a list of tickets. Limit sets the maximum amount of tickets to return. Offset sets the offset from the first ticket in the list that should be returned. the `&nocom` param is optional and indicates that the comments of the tickets should not be sent to optimize page load times.

### Ticket from ID
`/tickets/<id>`

Used to receive a single ticket by ID.

Statuses:
- 2 - Solved
- 4 - Unsolved
- 8 - Archived

Tags:
- 2 - Problem
- 4 - Question

Returns:
```json
{
  "id": <ticket id (Long)>,
  "title": "<title>",
  "body": "<body>",
  "author": {
    "id": <user id (Long)>,
    "displayName": "<displayname>",
    "username": "<username>",
    "creationTime": <user creation time (Long)>
  },
  "comments": [<comments>],
  "upVoters": [<user ids>],
  "downVoters": [<user ids>],
  "tags": [<tags>],
  "status": <status>,
  "createdAt": <ticket creation time (Long)>
}
```

### User from ID
`/users/<id>`

Used to receive a single user by ID.

Returns:
```json
{
  "id": <user id (Long)>,
  "displayName": "<displayname>",
  "username": "<username>",
  "creationTime": <user creation time (Long)>
}
```

### Current user
`/user`

Used to receive the active user. If none is present, the following error will be returned: `400: Not logged in.`

Returns:
```json
{
  "id": <user id (Long)>,
  "displayName": "<displayname>",
  "username": "<username>",
  "creationTime": <user creation time (Long)>
}
```

### Search
`/search?query=<query>&limit=<limit>`

Used to search users or tickets. Query needs to be present. Limit limits the amount of results per type and is optional.

Returns:
```json
{
  "tickets": [
    <tickets>
  ],
  "users": [
    <users>
  ]
}
```

### Upvote
`/upvote`

Used to upvote a post.

Takes:
```json
{
  "id": <ticket id>
}
```

### Downvote
`/downvote`

Used to downvote a post.

Takes:
```json
{
  "id": <ticket id>
}
```

## Error handling
Every endpoint is made in a way that it can be used without needing to make sure the input is valid for the server. If the server does not accept the input, an according error will be returned. Mostly in the form of an `400: Bad Request`.
Everytime a request completes successfully, a `200: OK` is returned alongside the data.