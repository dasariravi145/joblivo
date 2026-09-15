# Joblivo Web Application

Production-ready web application frontend foundation for Joblivo.

## Technology Stack

- **Framework:** Next.js (App Router)
- **Language:** TypeScript
- **Styling:** Tailwind CSS
- **Code Quality:** ESLint

## Environment Configuration

Copy the example environment template to create your local environment:

```bash
cp .env.example .env.local
```

### Environment Variables

| Variable | Description | Default (Local) |
| :--- | :--- | :--- |
| `NEXT_PUBLIC_API_BASE_URL` | Backend API Base URL | `http://localhost:8080` |

> **Note:** Do NOT commit `.env.local` or any secret-bearing files to version control.

## Getting Started

Run the development server:

```bash
npm run dev
```

Build the production application:

```bash
npm run build
```

Run ESLint:

```bash
npm run lint
```
