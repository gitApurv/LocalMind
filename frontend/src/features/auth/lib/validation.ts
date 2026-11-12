/** Must match the backend `RegisterRequest` limits; BCrypt ignores bytes past 72. */
export const MIN_PASSWORD_LENGTH = 8;
export const MAX_PASSWORD_LENGTH = 72;
const MIN_FULL_NAME_LENGTH = 2;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export interface LoginFields {
  email: string;
  password: string;
}

export interface RegisterFields extends LoginFields {
  fullName: string;
}

/** Returns the first problem with the login form, or null when it can be submitted. */
export function validateLogin({ email, password }: LoginFields): string | null {
  return validateEmail(email) ?? (password ? null : "Password is required");
}

/** Returns the first problem with the registration form, or null when it can be submitted. */
export function validateRegister({ fullName, email, password }: RegisterFields): string | null {
  return validateFullName(fullName) ?? validateEmail(email) ?? validateNewPassword(password);
}

function validateFullName(fullName: string): string | null {
  if (!fullName.trim()) return "Full name is required";
  if (fullName.trim().length < MIN_FULL_NAME_LENGTH) return "Name must be at least 2 characters";
  return null;
}

function validateEmail(email: string): string | null {
  if (!email.trim()) return "Email address is required";
  if (!EMAIL_PATTERN.test(email)) return "Please enter a valid email address";
  return null;
}

function validateNewPassword(password: string): string | null {
  if (!password) return "Password is required";
  if (password.length < MIN_PASSWORD_LENGTH) return "Password must be at least 8 characters long";
  if (password.length > MAX_PASSWORD_LENGTH) return "Password cannot exceed 72 characters";
  return null;
}
