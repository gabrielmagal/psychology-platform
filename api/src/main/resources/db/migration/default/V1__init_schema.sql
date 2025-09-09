CREATE TABLE user_info (
     id UUID PRIMARY KEY,
     registered_by_keycloak_id VARCHAR(255),
     keycloak_id VARCHAR(255) UNIQUE,
     cpf VARCHAR(11) NOT NULL UNIQUE,
     first_name VARCHAR(255) NOT NULL,
     last_name VARCHAR(255) NOT NULL,
     email VARCHAR(255) NOT NULL,
     phone_number VARCHAR(255) UNIQUE NOT NULL,
     birth_date DATE NOT NULL,
     profile_image TEXT,
     user_type VARCHAR(50) NOT NULL
);

CREATE TABLE session_package (
     id UUID PRIMARY KEY,
     patient_id UUID NOT NULL REFERENCES user_info(id),
     psychologist_id UUID NOT NULL REFERENCES user_info(id),
     package_title VARCHAR(255),
     total_sessions INT
);

CREATE TABLE payment (
     id UUID PRIMARY KEY,
     amount NUMERIC(19, 2) NOT NULL,
     payment_date DATE,
     paid BOOLEAN,
     observation VARCHAR(255),
     receipt TEXT,
     receipt_name VARCHAR(255),
     receipt_type VARCHAR(255),
     payment_method VARCHAR(50) NOT NULL,
     session_package_id UUID REFERENCES session_package(id) ON DELETE CASCADE
);

CREATE TABLE session_info (
      id UUID PRIMARY KEY,
      date_session DATE,
      title VARCHAR(255),
      summary VARCHAR(255),
      private_notes TEXT,
      attended_at TIMESTAMP,
      session_package_id UUID REFERENCES session_package(id) ON DELETE CASCADE
);

CREATE TABLE annotation (
    id UUID PRIMARY KEY,
    main_feeling VARCHAR(255) NOT NULL,
    significant_events VARCHAR(255) NOT NULL,
    current_phase VARCHAR(255) NOT NULL,
    dominant_thought TEXT NOT NULL,
    intervention VARCHAR(255) NOT NULL,
    session_id UUID NOT NULL REFERENCES session_info(id) ON DELETE CASCADE
);

CREATE TABLE mercado_pago_info (
   id UUID PRIMARY KEY REFERENCES user_info(id),
   access_token VARCHAR(255) NOT NULL,
   refresh_token VARCHAR(255),
   scope VARCHAR(255),
   expires_in INTEGER,
   token_created_at TIMESTAMP
);


CREATE TABLE audit_log (
   id UUID PRIMARY KEY,
   entity_name VARCHAR(255) NOT NULL,
   entity_id VARCHAR(255) NOT NULL,
   action VARCHAR(50) NOT NULL,
   keycloak_user_id VARCHAR(255),
   old_value TEXT,
   new_value TEXT,
   timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
