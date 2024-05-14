# Queue Underflow Internal API Usage
## Documentation of Endpoints

### Signup
`/signup`

Used to create a new account. Sends an email containing a verification url to the specified email address. Hash needs to be the entered password hashed once with SHA-512.

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
- 1 - Problem
- 2 - Question

```json
{
  "title": "<title>",
  "body": "<body>",
  "tags": [
    1,
    2
  ]
}
```
### Comment
`/comment`

Used to comment on a ticket or comment with the currently active account. If no user is present, the following error will be returned: `400: Not logged in.`

```json
{
  "id": <object id (Long)>,
  "body": "<body>"
}
```

### Delete
`/delete`

Used to delete a ticket or comment with the currently active account. Operation will succeed if the user is the ticket/comment owner, or if he has admin permissions. If no user is present, the following error will be returned: `400: Not logged in.`

```json
{
  "id": <object id (Long)>
}
```

### Tickets
`/tickets?limit=<limit>&offset=<offset>&nocom`

Used to receive a list of tickets. Limit sets the maximum amount of tickets to return. Offset sets the offset from the first ticket in the list that should be returned. the `&nocom` param is optional and indicates that the comments of the tickets should not be sent to optimize page load times.

### Ticket from ID
`/tickets/<id>`

Used to receive a single ticket by ID.

### User from ID
`/users/<id>`

Used to receive a single user by ID.

### Current user
`/user`

Used to receive the active user. If none is present, the following error will be returned: `400: Not logged in.`

## Error handling
Every endpoint is made in a way that it can be used without needing to make sure the input is valid for the server. If the server does not accept the input, an according error will be returned. Mostly in the form of an `400: Bad Request`.
Everytime a request completes successfully, a `200: OK` is returned alongside the data.