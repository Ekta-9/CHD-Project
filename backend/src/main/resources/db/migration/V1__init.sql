-- Database Schema Migration V1
-- Written to be compatible with both H2 (PostgreSQL mode) and PostgreSQL

create table doctor (
  doctor_id varchar(36) primary key,
  full_name varchar(255) not null,
  email varchar(255) not null unique,
  phone varchar(50),
  is_active boolean not null default true,
  created_at timestamp not null default current_timestamp
);

create table doctor_auth (
  doctor_id varchar(36) primary key references doctor(doctor_id) on delete cascade,
  password_hash varchar(255) not null,
  mfa_enabled boolean not null default false,
  mfa_secret varchar(255),
  last_password_reset timestamp
);

create table doctor_crypto (
  doctor_id varchar(36) primary key references doctor(doctor_id) on delete cascade,
  public_key bytea not null,
  private_key_enc bytea not null,
  private_key_salt bytea not null,
  kek_params varchar(1000) not null
);

create table session (
  session_id varchar(36) primary key,
  doctor_id varchar(36) not null references doctor(doctor_id) on delete cascade,
  login_at timestamp not null default current_timestamp,
  last_activity_at timestamp not null default current_timestamp,
  logout_at timestamp,
  ended_by varchar(20),
  ip varchar(45),
  user_agent varchar(500)
);
create index idx_session_doctor_time on session(doctor_id, login_at);

create table audit_log (
  audit_id bigint generated always as identity primary key,
  session_id varchar(36) references session(session_id),
  doctor_id varchar(36) references doctor(doctor_id),
  action varchar(100) not null,
  entity_type varchar(100) not null,
  entity_id varchar(36),
  details varchar(5000),
  created_at timestamp not null default current_timestamp
);
create index idx_audit_doctor_time on audit_log(doctor_id, created_at);

create table patient (
  patient_id varchar(36) primary key,
  anonymized_code varchar(100) unique,
  enc_payload bytea not null,
  enc_payload_iv bytea not null,
  enc_payload_tag bytea not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp
);

create table patient_key (
  patient_id varchar(36) not null references patient(patient_id) on delete cascade,
  doctor_id varchar(36) not null references doctor(doctor_id) on delete cascade,
  wrapping_scheme varchar(50) not null,
  dek_enc bytea not null,
  dek_iv bytea not null,
  dek_tag bytea not null,
  primary key (patient_id, doctor_id)
);

create table patient_access (
  doctor_id varchar(36) references doctor(doctor_id) on delete cascade,
  patient_id varchar(36) references patient(patient_id) on delete cascade,
  role varchar(20) not null,
  granted_by varchar(36) references doctor(doctor_id),
  granted_at timestamp not null default current_timestamp,
  primary key (doctor_id, patient_id)
);

create table ecg_scan (
  scan_id varchar(36) primary key,
  patient_id varchar(36) not null references patient(patient_id) on delete cascade,
  storage_uri varchar(500) not null,
  mimetype varchar(100) not null,
  uploaded_by varchar(36) references doctor(doctor_id),
  uploaded_at timestamp not null default current_timestamp,
  checksum varchar(100),
  metadata varchar(2000)
);
create index idx_scan_patient_time on ecg_scan(patient_id, uploaded_at);

create table ml_result (
  result_id varchar(36) primary key,
  patient_id varchar(36) not null references patient(patient_id) on delete cascade,
  scan_id varchar(36) references ecg_scan(scan_id) on delete set null,
  model_version varchar(50) not null,
  predicted_label varchar(50) not null,
  class_probs varchar(1000) not null,
  explanation_uri varchar(500),
  threshold decimal(5,4) not null,
  created_by varchar(36) references doctor(doctor_id),
  created_at timestamp not null default current_timestamp
);
create index idx_ml_patient_time on ml_result(patient_id, created_at);

create table draft (
  draft_id varchar(36) primary key,
  doctor_id varchar(36) not null references doctor(doctor_id) on delete cascade,
  patient_id varchar(36) references patient(patient_id) on delete cascade,
  form_type varchar(100) not null,
  enc_payload bytea not null,
  enc_payload_iv bytea not null,
  enc_payload_tag bytea not null,
  updated_at timestamp not null default current_timestamp
);
