# Securely Pushing to GitHub (Protecting `.env` secrets)

To ensure your sensitive information like API keys, database credentials, or secret tokens do not end up on GitHub, follow these steps:

## 1. Add `.env` to your `.gitignore` file
Before you commit any changes, you **must** tell Git to ignore the `.env` file.

Open `D:\campusbuddy\.gitignore` and add the following line at the end:
```
.env
```

*Note: If you have different environment files (like `.env.local`, `.env.development`), you can add `*.env*` to ignore all of them.*

## 2. Verify `.env` is ignored
You can check if your `.env` file is being tracked by Git using the command:
```bash
git status
```
If `.env` is successfully ignored, it will **not** show up under "Untracked files".

## 3. Commit your `.gitignore`
Make sure to commit your updated `.gitignore` file first, so the rule is saved in your repository history:
```bash
git add .gitignore
git commit -m "chore: ignore .env file"
```

## 4. Push your code securely
Now you can safely add, commit, and push your remaining code:
```bash
git add .
git commit -m "your commit message"
git push origin main
```

## 5. (Optional) Create a `.env.example` file
It is best practice to create a dummy file named `.env.example` containing the *keys* but not the *values*.
Example:
```
API_KEY=your_api_key_here
DB_PASSWORD=your_db_password_here
```
You can commit and push the `.env.example` file so other developers know what variables are required to run the project.

---
**Warning:** If you accidentally commit and push a `.env` file, the secrets are compromised immediately. You will need to revoke those keys and generate new ones. Removing it in a later commit does **not** erase it from Git's history!
