## Deniz Çelik Halat - Industrial Product Catalog System

A professional, dynamic web-based catalog system developed for an industrial steel wire rope and lifting equipment company. This application provides a seamless experience for browsing products, viewing technical specifications, and managing content via a secure admin panel.

## ✨ Features

- **Dynamic Catalog:** Filterable product listings categorized by industry and type.
- **Detailed Specifications:** High-resolution product images alongside technical data table visuals.
- **Advanced Search:** Real-time search functionality covering product names and descriptions.
- **Admin Dashboard:** Full CRUD (Create, Read, Update, Delete) capabilities for authorized users.
- **Responsive UI:** Elegant Black & Gold theme with AOS (Animate On Scroll) library for a modern user experience.
- **Secure Authentication:** Protected administrative routes using Spring Security.

## 📸 Screenshots

| Home Page | Product Listing |
|---|---|
| ![Home Page](/screenshots/home.png) | ![Products](/screenshots/products.png) |

| Product Details & Tech Table | Admin Dashboard |
|---|---|
| ![Details](/screenshots/detail.png) | ![Admin](/screenshots/admin.png) |

> *Note: Create a `/screenshots` folder in your repository and upload your captures as `home.png`, `products.png`, etc. to make them visible here.*

## 🛠️ Tech Stack

- **Backend:** Java 17, Spring Boot 3.5.1
- **Security:** Spring Security (In-Memory Auth)
- **Database:** MySQL with Spring Data JPA
- **Migrations:** Flyway
- **Frontend:** Thymeleaf, Bootstrap 5, AOS Library
- **Build Tool:** Maven

## 🚀 Getting Started

## 📋 System Requirements
- **Java:** JDK 17
- **Database:** MySQL 8.0+
- **Build Tool:** Maven 3.8+

### Installation

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/DenizKaraman461/deniz-celik-halat-katalog.git](https://github.com/DenizKaraman461/deniz-celik-halat-katalog.git)
   cd deniz-celik-halat-katalog
Database Setup:
Create a schema named katalog in your MySQL server.

Configuration:
Update src/main/resources/application.properties with your database credentials:

Properties
spring.datasource.url=jdbc:mysql://localhost:3306/katalog
spring.datasource.username=your_username
spring.datasource.password=your_password
Prepare Uploads Directory:
Create an uploads folder in the root directory to store product images.

Run the application:

Bash
./mvnw spring-boot:run
The application will be available at http://localhost:8080.

🔒 Security
The administrative routes (/add, /edit/**, /delete/**) are protected. Use the shield icon on the navigation bar to access the login page.

📄 License
This project is developed for Kadir Karaman Deniz Çelik Halat. All rights reserved.

## 👤 Author

**Deniz Karaman**
- GitHub: [@DenizKaraman461](https://github.com/DenizKaraman461)
- LinkedIn: [Deniz Karaman](https://www.linkedin.com/in/deniz-karaman-4450a2352/)
- University: Izmir University of Economics - Computer Engineering