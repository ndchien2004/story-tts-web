import { useCallback, useEffect, useState } from "react";
import { adminApi, catalogApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import ConfirmDialog from "../../components/ConfirmDialog";
import {
  Alert,
  Button,
  EmptyState,
  Field,
  Spinner,
  TextArea,
  TextInput,
} from "../../components/ui";

/**
 * Genres and authors, side by side.
 *
 * The API for both has been complete since the beginning — create, rename,
 * delete — with nothing on screen to reach it, so the only way to fix a
 * misspelled genre was a database client. They share a page because they are
 * the same job: the two lists every story has to choose from.
 *
 * Each column is one form over one list. Editing loads the row into the form
 * above it rather than opening a dialog: the list is short, the fields are two,
 * and seeing the other names while retyping one of them is the whole point.
 *
 * Deleting is guarded on the server — a genre still in use refuses to go — so
 * the count beside each row is here to explain that *before* the click rather
 * than after it.
 */
export default function AdminCatalogPage() {
  const [genres, setGenres] = useState(null);
  const [authors, setAuthors] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const load = useCallback(() => {
    Promise.all([catalogApi.genres(), catalogApi.authors()])
      .then(([genreList, authorList]) => {
        setGenres(genreList);
        setAuthors(authorList);
      })
      .catch((err) => setError(err.message));
  }, []);

  useEffect(load, [load]);

  return (
    <AdminPage title="Thể loại & tác giả">
      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      <div className="admin-catalog">
        <CatalogColumn
          title="Thể loại"
          unit="thể loại"
          items={genres}
          secondLabel="Mô tả"
          secondField="description"
          secondHint="Hiện trong menu “Thể loại” trên thanh điều hướng."
          nameLabel="Tên thể loại"
          namePlaceholder="Kiếm hiệp, Trinh thám…"
          api={{
            create: adminApi.createGenre,
            update: adminApi.updateGenre,
            remove: adminApi.deleteGenre,
          }}
          onChanged={(message) => {
            setNotice(message);
            setError(null);
            load();
          }}
        />

        <CatalogColumn
          title="Tác giả"
          unit="tác giả"
          items={authors}
          secondLabel="Tiểu sử"
          secondField="bio"
          secondHint="Ghi chú ngắn về tác giả, chỉ dùng nội bộ."
          nameLabel="Tên tác giả"
          namePlaceholder="Nguyễn Nhật Ánh…"
          api={{
            create: adminApi.createAuthor,
            update: adminApi.updateAuthor,
            remove: adminApi.deleteAuthor,
          }}
          onChanged={(message) => {
            setNotice(message);
            setError(null);
            load();
          }}
        />
      </div>
    </AdminPage>
  );
}

/**
 * One list and its form. Genres and authors differ only in wording and in the
 * name of their second field, so they run through the same component rather
 * than through two near-identical copies.
 *
 * @param secondField the payload key of the free-text field: `description` or `bio`
 */
function CatalogColumn({
  title,
  unit,
  items,
  nameLabel,
  namePlaceholder,
  secondLabel,
  secondField,
  secondHint,
  api,
  onChanged,
}) {
  const [editing, setEditing] = useState(null);
  const [name, setName] = useState("");
  const [second, setSecond] = useState("");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState(null);

  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  function reset() {
    setEditing(null);
    setName("");
    setSecond("");
    setFormError(null);
  }

  function startEdit(item) {
    setEditing(item);
    setName(item.name);
    setSecond(item[secondField] ?? "");
    setFormError(null);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setFormError(`${nameLabel} không được để trống.`);
      return;
    }

    setSaving(true);
    setFormError(null);
    const payload = { name: trimmed, [secondField]: second.trim() || null };

    try {
      if (editing) {
        await api.update(editing.id, payload);
        onChanged(`Đã cập nhật ${unit} “${trimmed}”.`);
      } else {
        await api.create(payload);
        onChanged(`Đã thêm ${unit} “${trimmed}”.`);
      }
      reset();
    } catch (err) {
      // Stays beside the form: "tên đã tồn tại" is about what was just typed.
      setFormError(err.message);
    } finally {
      setSaving(false);
    }
  }

  async function confirmDelete() {
    setDeleting(true);
    try {
      await api.remove(pendingDelete.id);
      onChanged(`Đã xóa ${unit} “${pendingDelete.name}”.`);
      if (editing?.id === pendingDelete.id) reset();
      setPendingDelete(null);
    } catch (err) {
      setFormError(err.message);
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  const inUse = pendingDelete?.storyCount > 0;

  return (
    <section className="admin-panel">
      <div className="admin-panel-head">
        <span className="admin-panel-title">{title}</span>
        {items && (
          <span className="admin-stat">
            <b>{items.length}</b>
            <span>{unit}</span>
          </span>
        )}
      </div>

      <form className="admin-catalog-form" onSubmit={handleSubmit}>
        <Field label={nameLabel} htmlFor={`${secondField}-name`}>
          <TextInput
            id={`${secondField}-name`}
            value={name}
            placeholder={namePlaceholder}
            maxLength={150}
            onChange={(event) => setName(event.target.value)}
          />
        </Field>

        <Field label={secondLabel} htmlFor={`${secondField}-text`} hint={secondHint}>
          <TextArea
            id={`${secondField}-text`}
            value={second}
            rows={2}
            style={{ minHeight: "3.5rem", fontFamily: "var(--font-sans)" }}
            onChange={(event) => setSecond(event.target.value)}
          />
        </Field>

        {formError && <Alert tone="error">{formError}</Alert>}

        <div className="row">
          <Button type="submit" variant="primary" loading={saving}>
            {editing ? "Lưu thay đổi" : `+ Thêm ${unit}`}
          </Button>
          {editing && (
            <Button variant="ghost" disabled={saving} onClick={reset}>
              Hủy
            </Button>
          )}
        </div>
      </form>

      <div className="admin-panel-body scroll-area">
        {!items && <Spinner />}

        {items && items.length === 0 && (
          <EmptyState title={`Chưa có ${unit} nào`}>
            Dùng biểu mẫu bên trên để thêm mục đầu tiên.
          </EmptyState>
        )}

        {items && items.length > 0 && (
          <table className="nb-table">
            <thead>
              <tr>
                <th>Tên</th>
                <th>Truyện</th>
                <th className="admin-cell-actions">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className={editing?.id === item.id ? "admin-row-editing" : ""}>
                  <td className="admin-cell-main">
                    <div className="admin-title-cell">
                      <strong>{item.name}</strong>
                      {item[secondField] && <span className="muted">{item[secondField]}</span>}
                    </div>
                  </td>

                  <td>
                    <span className="tabular-num">{item.storyCount ?? 0}</span>
                  </td>

                  <td className="admin-cell-actions">
                    <Button size="sm" onClick={() => startEdit(item)}>
                      Sửa
                    </Button>
                    <Button
                      size="sm"
                      variant="danger"
                      // The server refuses either way; disabling here says why
                      // in the tooltip instead of spending a request to be told.
                      disabled={item.storyCount > 0}
                      title={
                        item.storyCount > 0
                          ? `Còn ${item.storyCount} truyện đang dùng ${unit} này`
                          : `Xóa ${unit} này`
                      }
                      onClick={() => setPendingDelete(item)}
                    >
                      Xóa
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title={`Xóa ${unit}?`}
        message={`${title} “${pendingDelete?.name}” sẽ bị xóa khỏi hệ thống.`}
        detail={
          inUse
            ? `Còn ${pendingDelete.storyCount} truyện đang dùng, máy chủ sẽ từ chối xóa.`
            : "Thao tác này không thể hoàn tác."
        }
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </section>
  );
}
