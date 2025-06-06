CREATE TABLE usuario (
     id UUID PRIMARY KEY,
     keycloak_id VARCHAR(255) UNIQUE,
     cpf VARCHAR(11) NOT NULL UNIQUE,
     name VARCHAR(255) NOT NULL,
     email VARCHAR(255) NOT NULL,
     phone_number VARCHAR(255) UNIQUE NOT NULL,
     birth_date DATE NOT NULL,
     user_type VARCHAR(50) NOT NULL
);

CREATE TABLE session_package (
     id UUID PRIMARY KEY,
     patient_id UUID NOT NULL REFERENCES usuario(id),
     psychologist_id UUID NOT NULL REFERENCES usuario(id),
     package_title VARCHAR(255),
     total_sessions INT
);

CREATE TABLE payment (
     id UUID PRIMARY KEY,
     amount NUMERIC(19, 2) NOT NULL,
     payment_date DATE NOT NULL,
     paid BOOLEAN,
     observation VARCHAR(255),
     receipt TEXT,
     receipt_name VARCHAR(255) NOT NULL,
     receipt_type VARCHAR(255) NOT NULL,
     payment_method VARCHAR(50) NOT NULL,
     session_package_id UUID REFERENCES session_package(id) ON DELETE CASCADE
);

CREATE TABLE session_care (
      id UUID PRIMARY KEY,
      date_session DATE,
      title VARCHAR(255),
      summary VARCHAR(255),
      private_notes TEXT,
      attended_at TIMESTAMP,
      patient_id UUID NOT NULL REFERENCES usuario(id),
      psychologist_id UUID NOT NULL REFERENCES usuario(id),
      session_package_id UUID REFERENCES session_package(id) ON DELETE CASCADE
);

CREATE TABLE annotation (
    id UUID PRIMARY KEY,
    mainFeeling VARCHAR(255) NOT NULL,
    significantEvents VARCHAR(255) NOT NULL,
    currentPhase VARCHAR(255) NOT NULL,
    dominantThought TEXT NOT NULL,
    intervention VARCHAR(255) NOT NULL,
    session_id UUID NOT NULL REFERENCES session_care(id) ON DELETE CASCADE
);