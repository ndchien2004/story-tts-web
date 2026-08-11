import { ButtonLink } from "../components/ui";

export default function NotFoundPage() {
  return (
    <div className="container-narrow">
      {/* No frame around it: the page is the message, not a card sitting on
          one. */}
      <div className="text-center stack" style={{ alignItems: "center" }}>
        <span style={{ fontSize: "4rem", fontWeight: 300 }}>404</span>
        <h1>Không tìm thấy trang</h1>
        <p className="muted">Đường dẫn bạn truy cập không tồn tại hoặc đã bị xóa.</p>
        <ButtonLink to="/" variant="primary" size="lg">
          Về trang chủ
        </ButtonLink>
      </div>
    </div>
  );
}
