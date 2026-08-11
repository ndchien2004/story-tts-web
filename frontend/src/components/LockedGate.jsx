import { useLocation } from "react-router-dom";
import { useAuth } from "../context/auth-context";
import { ButtonLink } from "./ui";

/**
 * Shown in place of chapter content when the API answers 403.
 *
 * The wording branches on `requiredAccessLevel` so the reader is told exactly
 * what is missing — signing in, or a VIP upgrade.
 */
export default function LockedGate({ requiredAccessLevel, message }) {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  const needsVip = requiredAccessLevel === "VIP";
  const canFixByLoggingIn = !isAuthenticated;

  return (
    <div className="locked-gate">
      <span className="locked-gate-tag">{needsVip ? "Nội dung VIP" : "Nội dung hạn chế"}</span>

      <h2>{needsVip ? "Chương dành cho thành viên VIP" : "Chương yêu cầu đăng nhập"}</h2>

      <p className="muted" style={{ maxWidth: "44ch" }}>
        {message ??
          (needsVip
            ? "Bạn cần tài khoản VIP để đọc và nghe chương này."
            : "Vui lòng đăng nhập để tiếp tục đọc chương này.")}
      </p>

      {canFixByLoggingIn ? (
        <div className="row" style={{ justifyContent: "center" }}>
          <ButtonLink to="/dang-nhap" state={{ from: location }} variant="primary" size="lg">
            Đăng nhập
          </ButtonLink>
          <ButtonLink to="/dang-ky" size="lg">
            Đăng ký tài khoản
          </ButtonLink>
        </div>
      ) : (
        <p style={{ fontWeight: 500 }}>
          Tài khoản của bạn chưa được cấp quyền VIP. Vui lòng liên hệ quản trị viên để nâng cấp.
        </p>
      )}
    </div>
  );
}
