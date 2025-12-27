# URL Shortener Frontend

A modern, AI-powered URL shortener frontend built with React, TypeScript, and Tailwind CSS.

## Features

- 🔗 **Shorten URLs**: Create short links with custom aliases.
- 🤖 **AI Analysis**: Automatic content summarization, categorization, and safety scoring (via Ollama).
- 📊 **Deep Analytics**: Visualize clicks by country, device, browser, and time.
- 📱 **QR Codes**: Generate and download QR codes.
- 🔒 **Security**: Password protection and expiration dates.
- ⚡ **Fast**: Optimized for performance with Vite.

## Tech Stack

- **Framework**: React 18 + TypeScript + Vite
- **Styling**: Tailwind CSS + Framer Motion
- **Charts**: Recharts
- **Icons**: Lucide React
- **HTTP**: Axios
- **Notifications**: React Hot Toast

## Getting Started

### Prerequisites

- Node.js 18+
- The Backend service running on port 8080

### Installation

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start the development server:
   ```bash
   npm run dev
   ```

3. Open [http://localhost:5173](http://localhost:5173)

## Project Structure

```
src/
├── components/       # Reusable UI components
│   ├── AnalyticsDashboard.tsx  # Charts & Stats
│   ├── Layout.tsx             # Navbar & Footer
│   ├── ResultCard.tsx         # Success state & AI results
│   └── UrlShortenerForm.tsx   # Main creation form
├── pages/
│   ├── HomePage.tsx           # Landing page
│   └── AnalyticsPage.tsx      # Analytics search & view
├── services/
│   └── api.ts                 # API client
└── types.ts                   # TypeScript interfaces
```
