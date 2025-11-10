# Project Structure Exploration - Complete Index

This document serves as an index to the comprehensive analysis of the DORA Metrics Platform Frontend project structure.

## Documents Generated

### 1. Executive Summary
**File:** `executive_summary.md`
**Best For:** Quick overview of the entire project
**Contains:**
- Project overview and tech stack
- High-level description of each architectural component
- Key findings for repositories feature implementation
- Critical architectural patterns
- Reference files for implementation

**Read This First** for a 5-minute understanding of the project.

---

### 2. Project Structure Analysis
**File:** `project_structure_analysis.md`
**Best For:** Detailed technical understanding
**Contains:**
- Detailed breakdown of 8 key architectural areas:
  1. Pages/Routes structure
  2. Data fetching & API client pattern (NO TanStack Query!)
  3. UI Component Library confirmation (Shadcn/UI)
  4. Authentication & RBAC implementation
  5. API Client & Services
  6. Complete project file structure
  7. Testing patterns
  8. Environment configuration
- Key observations for repositories feature
- Reference files for implementation

**Read This For:** In-depth understanding of how each part works.

---

### 3. Architecture Diagrams & Data Flow
**File:** `architecture_diagram.md`
**Best For:** Visual understanding of system architecture
**Contains:**
- Application architecture diagram
- State management & component hierarchy
- Data fetching flow diagram
- API endpoints structure
- Component composition patterns
- Type hierarchy
- Testing pattern overview

**Read This For:** Visual learners and understanding how components interact.

---

### 4. Implementation Reference Guide
**File:** `implementation_reference.md`
**Best For:** Code templates and implementation checklist
**Contains:**
- Quick reference for type definitions (RepositoryDto, etc.)
- Full code templates for:
  - Page component (RepositoriesPage)
  - Table component (RepositoriesTable)
  - Routing setup
  - Navigation setup
  - Test file template
- Implementation checklist
- Common patterns with code examples
- Files to create/modify summary
- Development commands
- Key implementation insights (10 points)

**Read This For:** Actual implementation of the repositories feature.

---

## Quick Navigation by Question

### "What is the tech stack?"
See: **Executive Summary** - Key Technologies section

### "How are routes structured?"
See: **Project Structure Analysis** - Section 1: Pages/Routes Structure

### "How does data fetching work?"
See: **Project Structure Analysis** - Section 2: Data Fetching & API Client Pattern

### "Is TanStack Query used?"
See: **Executive Summary** - "No TanStack Query" note
See: **Project Structure Analysis** - "No TanStack Query / React Query" section

### "What UI component library is used?"
See: **Executive Summary** - "UI Component Library - Shadcn/UI (Confirmed)"
See: **Project Structure Analysis** - Section 3: UI Component Library

### "How is authentication implemented?"
See: **Project Structure Analysis** - Section 4: Authentication & RBAC
See: **Architecture Diagrams** - Auth flow section

### "How are roles checked?"
See: **Executive Summary** - "Role Checking" section
See: **Implementation Reference** - "Pattern 2: Auth Check with Context"

### "Where are API endpoints defined?"
See: **Architecture Diagrams** - "API Endpoints Structure" section
See: **Project Structure Analysis** - Section 5: API Client & Services

### "Where are component files located?"
See: **Project Structure Analysis** - Section 6: Project Structure
See: **Architecture Diagrams** - State Management & Component Hierarchy

### "How are tests written?"
See: **Project Structure Analysis** - Section 7: Testing Pattern
See: **Implementation Reference** - "Pattern 4: Vitest Component Testing"

### "How do I implement the repositories feature?"
See: **Implementation Reference** - All code templates and checklist

---

## Key Findings Summary

### Technology Stack
- React 19.1.1 (latest)
- TypeScript 5.8.3 (strict mode)
- Vite 7.1.2 (build tool)
- React Router 7.8.2 (routing)
- Tailwind CSS 4.1.12 (styling)
- Shadcn/UI (UI components)
- Vitest 3.2.4 (testing)

### Data Fetching
- **NO TanStack Query** - Uses vanilla Fetch API
- **NO axios** - Native fetch() with credentials: 'include'
- **NO custom hooks** - Inline useEffect + useState
- **Pattern:** Try/catch in useEffect with loading/error states

### Authentication
- **GitHub OAuth 2.0** via `/oauth2/authorization/github`
- **Session-based** with HTTP-only cookies
- **Context API** for user state (AuthenticatedLayout)
- **Role-based access** with simple string matching

### UI Components
- **Shadcn/UI** components (Button, Card, Table, Avatar)
- **Custom components** (RoleBadge, UsersTable, SideNav)
- **Tailwind CSS** for all styling
- **No external CSS files**

### Project Structure
```
src/
├── pages/          # Page components
├── components/     # UI and custom components
│   └── ui/        # Shadcn/UI components
├── layouts/       # Layout wrappers
├── types/         # TypeScript definitions
└── lib/           # Utilities
```

### State Management
- **Authentication:** React Context API in AuthenticatedLayout
- **Component State:** useState for local component state
- **No global state library** (Redux, Zustand, etc.)

---

## Files to Reference When Implementing Repositories Feature

### Core Reference Files (in project)

| File | Lines | Purpose |
|------|-------|---------|
| `/src/pages/UserManagementPage.tsx` | 47 | Template for page structure |
| `/src/components/UsersTable.tsx` | 62 | Template for table component |
| `/src/types/user.types.ts` | 39 | Where to add new types |
| `/src/layouts/AuthenticatedLayout.tsx` | 89 | Auth context & useAuth() |
| `/src/components/SideNav.tsx` | 47 | Navigation with role checking |
| `/src/pages/UserManagementPage.test.tsx` | 80 | Testing template |
| `/src/App.tsx` | 23 | Route definitions |

### Files to Create

1. `/src/types/repository.types.ts` - Add to existing user.types.ts
2. `/src/pages/RepositoriesPage.tsx` - Main page component
3. `/src/pages/RepositoriesPage.test.tsx` - Page tests
4. `/src/components/RepositoriesTable.tsx` - Table component
5. `/src/components/RepositoriesTable.test.tsx` - Table tests (optional)

### Files to Modify

1. `/src/App.tsx` - Add route
2. `/src/components/SideNav.tsx` - Add navigation link

---

## Implementation Path

### Step 1: Type Definitions
Add RepositoryDto types to `/src/types/user.types.ts`

### Step 2: Page Component
Create `/src/pages/RepositoriesPage.tsx` following UserManagementPage pattern

### Step 3: Table Component
Create `/src/components/RepositoriesTable.tsx` following UsersTable pattern

### Step 4: Routing
Update `/src/App.tsx` to add new route

### Step 5: Navigation
Update `/src/components/SideNav.tsx` to add link

### Step 6: Testing
Create test files for page and table components

### Step 7: Verify
- Run `npm run dev` to test locally
- Run `npm run test` to verify tests pass
- Run `npm run lint` to check code style
- Run `npm run build` to verify production build

---

## Critical Insights

### 1. No Service Layer
API calls are inline in components. This is by design in this project. Keep repositories feature consistent with this pattern.

### 2. Fetch Pattern is Rigid
The try/catch/finally pattern in UserManagementPage is the standard. Follow it exactly for consistency.

### 3. Always Include credentials
Every fetch call includes `{ credentials: 'include' }` to maintain session cookies.

### 4. API URL Logic is Important
Always resolve API URL:
```tsx
const apiUrl = import.meta.env.DEV ? '' : import.meta.env.VITE_API_BASE_URL;
```

### 5. Type Safety is Valued
TypeScript is in strict mode. All types must be properly defined.

### 6. Tests are Expected
Existing test files show expectations for testing patterns. New components should be tested.

### 7. Role-Based Access is Simple
Just check `user?.roles.includes('ADMIN')` - no complex permission system.

### 8. Tailwind Only
No CSS files needed. Use Tailwind classes directly in JSX.

### 9. Shadcn/UI is Ready
Don't add new UI libraries. All needed components are already imported.

### 10. Vitest Mocking Pattern
Mock components with `vi.mock()`, mock fetch with `vi.spyOn()`.

---

## API Endpoints Available

### For Implementation

```
GET  /api/v1/repositories
  Response: RepositoryDto[]
  [
    {
      id: 1,
      repositoryUrl: "https://github.com/user/repo",
      datadogServiceName: "service-name" or null,
      owner: "user",
      repoName: "repo"
    }
  ]

POST /api/v1/repositories/sync
  Response: RepositorySyncResultDto
  {
    newRepositories: 5,
    totalRepositories: 12,
    unchanged: 7
  }

PUT  /api/v1/repositories/{id}
  Request: UpdateRepositoryRequest
  { datadogServiceName: "new-service" or null }
  Response: RepositoryDto
```

---

## Environment Setup

### Development
- Vite proxy to http://localhost:8080
- No env vars needed (empty string API URL)
- HMR enabled for hot reloads

### Production
- Set `VITE_API_BASE_URL` environment variable
- Build with `npm run build`
- Static assets in `/dist`

---

## Testing Strategy

### Unit Tests
- Mock sub-components with `vi.mock()`
- Mock fetch with `vi.spyOn(window, 'fetch')`
- Test loading, success, and error states
- Clean up with `vi.restoreAllMocks()`

### Manual Testing
- `npm run dev` starts dev server
- Open http://localhost:5173 (default Vite port)
- Test auth flow with GitHub
- Test navigation and permissions

---

## Build & Deployment

### Local Development
```bash
npm install
npm run dev          # Start with HMR
npm run test         # Run tests
npm run lint         # Check code
```

### Production Build
```bash
npm run build        # Builds to /dist
npm run preview      # Preview production build locally
```

### Deployment
- Build produces static files in `/dist`
- Serve with any static file server
- Set `VITE_API_BASE_URL` env var before building
- Backend must be running (configured in env)

---

## Conclusion

This is a **clean, minimal, and well-structured** React application perfectly positioned for adding the repositories management feature.

The existing UserManagementPage and associated components provide an excellent template that can be adapted for repositories with minimal changes.

**Next Steps:**
1. Read the Executive Summary (5 min)
2. Read the Implementation Reference (10 min)
3. Use the code templates to implement (30-60 min)
4. Follow the testing pattern for verification
5. Integrate into the routing and navigation system

All tools and infrastructure are already in place. The implementation is straightforward following the established patterns.