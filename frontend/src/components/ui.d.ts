import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from "react";
import type { LinkProps } from "react-router-dom";

/**
 * Khai báo kiểu cho bộ giao diện dùng chung.
 *
 * `ui.jsx` viết bằng JavaScript và sẽ ở nguyên như vậy — nó đã chạy tốt, và
 * viết lại cả bộ chỉ để có kiểu là việc rủi ro mà không đổi lấy được gì. Nhưng
 * những khối viết bằng TypeScript thì cần biết nó nhận gì, và nếu để TypeScript
 * tự suy ra từ mã JavaScript thì mọi tham số được rã ra sẽ thành bắt buộc — kể
 * cả `variant` hay `size` vốn có mặc định.
 *
 * Tệp này là chỗ nói rõ điều đó, một lần, cho cả hai bên cùng dùng.
 */

export type Tone = "neutral" | "info" | "success" | "warning" | "error" | "violet";
export type Variant = "default" | "primary" | "ghost" | "danger";
export type Size = "sm" | "md" | "lg";

interface StyledButton {
  variant?: Variant | string;
  size?: Size | string;
  block?: boolean;
  loading?: boolean;
  className?: string;
}

export function Button(
  props: StyledButton & ButtonHTMLAttributes<HTMLButtonElement>,
): JSX.Element;

export function ButtonLink(props: StyledButton & LinkProps): JSX.Element;

export function Badge(
  props: { tone?: Tone | string; className?: string; children?: ReactNode },
): JSX.Element;

export function Alert(
  props: { tone?: Tone | string; title?: ReactNode; children?: ReactNode },
): JSX.Element;

export function Spinner(props: { label?: string }): JSX.Element;

export function EmptyState(props: { title?: ReactNode; children?: ReactNode }): JSX.Element;

export function AnimatedNumber(
  props: { value: number; decimals?: number; duration?: number; className?: string },
): JSX.Element;

export function Field(
  props: {
    label?: ReactNode;
    htmlFor?: string;
    error?: ReactNode;
    hint?: ReactNode;
    children?: ReactNode;
  },
): JSX.Element;

export function TextInput(
  props: { error?: ReactNode } & InputHTMLAttributes<HTMLInputElement>,
): JSX.Element;

export function PasswordInput(
  props: { error?: ReactNode } & InputHTMLAttributes<HTMLInputElement>,
): JSX.Element;

export function TextArea(
  props: { error?: ReactNode } & TextareaHTMLAttributes<HTMLTextAreaElement>,
): JSX.Element;

export function Select(
  props: SelectHTMLAttributes<HTMLSelectElement>,
): JSX.Element;

/** Ô đánh dấu vẽ thành công tắc gạt; ô input thật vẫn nằm trong DOM. */
export function Switch(
  props: { label?: ReactNode; hint?: ReactNode } & InputHTMLAttributes<HTMLInputElement>,
): JSX.Element;

export function ChevronIcon(props: { right?: boolean }): JSX.Element;
