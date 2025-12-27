# 🔗 ShortAI - URL Shortener

A full-stack, production-ready URL shortener with advanced analytics and AI features, built with React, Spring Boot, PostgreSQL, and Redis.

## ✨ Features

### 🎯 Core Features
- **URL Shortening** - Generate short, memorable URLs with Base62 encoding
- **Custom Aliases** - Create branded short links (e.g., `/my-brand`)
- **QR Code Generation** - Automatic QR codes with customizable colors
- **Password Protection** - Secure sensitive links with passwords
- **Bulk URL Creation** - Create up to 100 URLs in a single request
- **URL Expiration** - Set automatic expiry for temporary links

### 📊 Advanced Analytics
- **Device Analytics** - Desktop, mobile, tablet breakdown
- **Browser & OS Stats** - Usage tracking
- **Referrer Tracking** - Traffic source analysis
- **Click Trends** - Visual charts and time-series data

### 🤖 AI Features (Ollama + llama3.2:1b)
- **Content Summarization** - AI-generated summaries of linked content
- **Auto-Categorization** - Automatic category detection
- **Safety Scoring** - AI-powered phishing/malware detection
- **Alias Suggestions** - Memorable alias recommendations

### 🛡️ Security & Performance
- **Rate Limiting** - 100 requests/minute/IP with Redis
- **Circuit Breaker** - Resilience4j for fault tolerance
- **Cache-First Strategy** - <50ms redirect latency
- **BCrypt Passwords** - Secure password hashing

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | React 18, TypeScript, Vite, Framer Motion, Recharts |
| Backend | Java 17, Spring Boot 3.2, Spring Data JPA |
| Database | PostgreSQL 15 |
| Cache | Redis 7 |
| AI (Optional) | Ollama + llama3.2:1b |
| API Docs | OpenAPI/Swagger |
| Container | Docker, Docker Compose |

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Ollama (optional, for AI features)

### 1. Clone & Start

```bash
# Clone the repository
git clone https://github.com/Shivam-2310/ShortAI.git
cd url-shortener

# Start all services
docker-compose up -d

# Optional: Start Ollama for AI features
ollama pull llama3.2:1b
ollama serve
```

### 2. Access the Application

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| API Docs | http://localhost:8080/api-docs |

## 📁 Project Structure

```
url-shortener/
├── frontend/                # React frontend
│   ├── src/
│   │   ├── api/            # API service functions
│   │   ├── components/     # React components
│   │   │   └── ui/         # Reusable UI components
│   │   ├── pages/          # Page components
│   │   └── types/          # TypeScript types
│   ├── Dockerfile
│   └── package.json
├── backend/                 # Spring Boot backend
│   ├── src/main/java/
│   │   └── com/urlshortener/
│   │       ├── controller/ # REST controllers
│   │       ├── service/    # Business logic
│   │       ├── repository/ # Data access
│   │       ├── entity/     # JPA entities
│   │       └── dto/        # Data transfer objects
│   ├── Dockerfile
│   └── pom.xml
├── docker-compose.yml       # Full stack deployment
└── README.md
```

## 🔌 API Endpoints

### URL Management
```http
POST /api/urls              # Create short URL
POST /api/urls/bulk         # Bulk create URLs
GET  /api/urls/{key}/stats  # Get basic stats
GET  /api/urls/{key}/analytics  # Get detailed analytics
GET  /api/urls/{key}/qrcode # Generate QR code
```

### Optional AI Endpoints
```http
POST /api/ai/analyze        # Full AI analysis
POST /api/ai/safety-check   # Safety check
POST /api/ai/suggest-alias  # Alias suggestions
POST /api/ai/summarize      # Summarize content
GET  /api/ai/status         # AI service status
```

### Redirect
```http
GET  /{shortKey}            # Redirect to original URL
POST /{shortKey}/unlock     # Unlock password-protected URL
```

## 🧪 Development

### Frontend Development

```bash
cd frontend
npm install
npm run dev     # Start dev server on port 3000
```

### Backend Development

```bash
cd backend

# Start dependencies
docker-compose -f docker-compose.local.yml up -d postgres redis

# Run Spring Boot
./mvnw spring-boot:run
```

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | localhost |
| `DB_PORT` | PostgreSQL port | 5432 |
| `REDIS_HOST` | Redis host | localhost |
| `APP_BASE_URL` | Base URL for short links | http://localhost:8080 |
| `OLLAMA_BASE_URL` | Ollama API URL (optional) | http://localhost:11434 |
| `OLLAMA_MODEL` | Ollama model (optional) | llama3.2:1b |

## 📈 Performance Targets

- ⚡ Redirect latency: <50ms (cache hit)
- 📊 Cache hit ratio: >90%
- 📱 QR generation: <100ms
- 🤖 AI analysis: <5s (optional, with caching)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request


---

Built with ❤️ using React, Spring Boot, PostgreSQL, and Redis
