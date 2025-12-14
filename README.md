# QuickLink

A modern, fast and scalable URL shortening platform built for performance and reliability.

## Features

- **Lightning Fast**: Optimized for high-performance URL shortening and redirection
- **Scalable Architecture**: Designed to handle millions of URLs and redirects
- **Modern Stack**: Built with cutting-edge technologies for reliability
- **Analytics**: Track clicks, geographic data, and usage statistics
- **Custom Aliases**: Create memorable short links with custom aliases
- **API-First**: RESTful API for seamless integration

## Quick Start

```bash
# Clone the repository
git clone https://github.com/yourusername/quicklink.git
cd quicklink

# Install dependencies
npm install

# Start development server
npm run dev
```

## API Usage

### Shorten URL
```bash
POST /api/shorten
{
  "url": "https://example.com/very-long-url",
  "alias": "custom-alias" // optional
}
```

### Access Short URL
```bash
GET /{shortCode}
# Redirects to original URL
```

## Tech Stack

- **Backend**: Node.js / Express
- **Database**: Redis / PostgreSQL
- **Frontend**: React / Next.js
- **Deployment**: Docker / Kubernetes

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For support and questions, please open an issue or contact us at support@quicklink.dev

## Features
The System is designed to support short url generation for the next 10 years
It gets x number of requests for a second
So max number of requests that it will handle in next 10 years are 10*365*24*60*60*x
characters allowed in url [a-zA-Z0-9] => total chars are 62
size of the short url 7
Total possible combinations = 62^7 = 3.5 trillion

## Archtecture Evolution

![alt text](image-1.png)
![alt text](image.png)
![alt text](image.png)