import os
import re

languages = {
    "ca": """
    <string name="progress_dialog_title">Actualitzar progrés</string>
    <string name="progress_label">Progrés:</string>
    <string name="progress_dialog_hint">Pàgina o percentatge</string>
    <string name="progress_pages">pàgines</string>
    <string name="progress_percent">percentatge</string>
    <string name="progress_post_to_feed">Publicar al fil</string>
    <string name="progress_comment_label">Comentari:</string>
    <string name="progress_comment_hint">Escriu un comentari…</string>
    <string name="progress_spoiler_alert">Incloure alerta d\\'espòiler</string>
    <string name="progress_spoiler_hint">Text de l\\'espòiler…</string>
    <string name="progress_privacy_public">Públic</string>
    <string name="progress_privacy_followers">Seguidors</string>
    <string name="progress_privacy_private">Privat</string>
    <string name="progress_btn_share">Compartir</string>
    <string name="progress_btn_save">Desar</string>
    <string name="progress_btn_cancel">Cancel·lar</string>
    <string name="progress_success">Estat actualitzat</string>
    <string name="progress_error">Error: %1$s</string>
    <string name="progress_network_error">Fallo de xarxa: %1$s</string>""",
    "cs": """
    <string name="progress_dialog_title">Aktualizovat pokrok</string>
    <string name="progress_label">Pokrok:</string>
    <string name="progress_dialog_hint">Stránka nebo procento</string>
    <string name="progress_pages">stránek</string>
    <string name="progress_percent">procento</string>
    <string name="progress_post_to_feed">Zveřejnit v kanálu</string>
    <string name="progress_comment_label">Komentář:</string>
    <string name="progress_comment_hint">Napište komentář…</string>
    <string name="progress_spoiler_alert">Zahrnout upozornění na spoiler</string>
    <string name="progress_spoiler_hint">Text spoileru…</string>
    <string name="progress_privacy_public">Veřejné</string>
    <string name="progress_privacy_followers">Sledující</string>
    <string name="progress_privacy_private">Soukromé</string>
    <string name="progress_btn_share">Sdílet</string>
    <string name="progress_btn_save">Uložit</string>
    <string name="progress_btn_cancel">Zrušit</string>
    <string name="progress_success">Stav aktualizován</string>
    <string name="progress_error">Chyba: %1$s</string>
    <string name="progress_network_error">Chyba sítě: %1$s</string>""",
    "de": """
    <string name="progress_dialog_title">Fortschritt aktualisieren</string>
    <string name="progress_label">Fortschritt:</string>
    <string name="progress_dialog_hint">Seite oder Prozent</string>
    <string name="progress_pages">Seiten</string>
    <string name="progress_percent">Prozent</string>
    <string name="progress_post_to_feed">Im Feed posten</string>
    <string name="progress_comment_label">Kommentar:</string>
    <string name="progress_comment_hint">Schreibe einen Kommentar…</string>
    <string name="progress_spoiler_alert">Spoiler-Warnung hinzufügen</string>
    <string name="progress_spoiler_hint">Spoiler-Text…</string>
    <string name="progress_privacy_public">Öffentlich</string>
    <string name="progress_privacy_followers">Follower</string>
    <string name="progress_privacy_private">Privat</string>
    <string name="progress_btn_share">Teilen</string>
    <string name="progress_btn_save">Speichern</string>
    <string name="progress_btn_cancel">Abbrechen</string>
    <string name="progress_success">Status aktualisiert</string>
    <string name="progress_error">Fehler: %1$s</string>
    <string name="progress_network_error">Netzwerkfehler: %1$s</string>""",
    "el": """
    <string name="progress_dialog_title">Ενημέρωση προόδου</string>
    <string name="progress_label">Πρόοδος:</string>
    <string name="progress_dialog_hint">Σελίδα ή ποσοστό</string>
    <string name="progress_pages">σελίδες</string>
    <string name="progress_percent">ποσοστό</string>
    <string name="progress_post_to_feed">Δημοσίευση στη ροή</string>
    <string name="progress_comment_label">Σχόλιο:</string>
    <string name="progress_comment_hint">Γράψτε ένα σχόλιο…</string>
    <string name="progress_spoiler_alert">Προσθήκη ειδοποίησης spoiler</string>
    <string name="progress_spoiler_hint">Κείμενο spoiler…</string>
    <string name="progress_privacy_public">Δημόσιο</string>
    <string name="progress_privacy_followers">Ακόλουθοι</string>
    <string name="progress_privacy_private">Ιδιωτικό</string>
    <string name="progress_btn_share">Κοινοποίηση</string>
    <string name="progress_btn_save">Αποθήκευση</string>
    <string name="progress_btn_cancel">Ακύρωση</string>
    <string name="progress_success">Η κατάσταση ενημερώθηκε</string>
    <string name="progress_error">Σφάλμα: %1$s</string>
    <string name="progress_network_error">Σφάλμα δικτύου: %1$s</string>""",
    "fr": """
    <string name="progress_dialog_title">Mettre à jour la progression</string>
    <string name="progress_label">Progression :</string>
    <string name="progress_dialog_hint">Page ou pourcentage</string>
    <string name="progress_pages">pages</string>
    <string name="progress_percent">pourcentage</string>
    <string name="progress_post_to_feed">Publier dans le fil</string>
    <string name="progress_comment_label">Commentaire :</string>
    <string name="progress_comment_hint">Rédigez un commentaire…</string>
    <string name="progress_spoiler_alert">Inclure une alerte spoiler</string>
    <string name="progress_spoiler_hint">Texte du spoiler…</string>
    <string name="progress_privacy_public">Public</string>
    <string name="progress_privacy_followers">Abonnés</string>
    <string name="progress_privacy_private">Privé</string>
    <string name="progress_btn_share">Partager</string>
    <string name="progress_btn_save">Enregistrer</string>
    <string name="progress_btn_cancel">Annuler</string>
    <string name="progress_success">Statut mis à jour</string>
    <string name="progress_error">Erreur : %1$s</string>
    <string name="progress_network_error">Erreur réseau : %1$s</string>""",
    "it": """
    <string name="progress_dialog_title">Aggiorna progresso</string>
    <string name="progress_label">Progresso:</string>
    <string name="progress_dialog_hint">Pagina o percentuale</string>
    <string name="progress_pages">pagine</string>
    <string name="progress_percent">percentuale</string>
    <string name="progress_post_to_feed">Pubblica nel feed</string>
    <string name="progress_comment_label">Commento:</string>
    <string name="progress_comment_hint">Scrivi un commento…</string>
    <string name="progress_spoiler_alert">Includi avviso spoiler</string>
    <string name="progress_spoiler_hint">Testo dello spoiler…</string>
    <string name="progress_privacy_public">Pubblico</string>
    <string name="progress_privacy_followers">Follower</string>
    <string name="progress_privacy_private">Privato</string>
    <string name="progress_btn_share">Condividi</string>
    <string name="progress_btn_save">Salva</string>
    <string name="progress_btn_cancel">Annulla</string>
    <string name="progress_success">Stato aggiornato</string>
    <string name="progress_error">Errore: %1$s</string>
    <string name="progress_network_error">Errore di rete: %1$s</string>""",
    "nl": """
    <string name="progress_dialog_title">Voortgang bijwerken</string>
    <string name="progress_label">Voortgang:</string>
    <string name="progress_dialog_hint">Pagina of percentage</string>
    <string name="progress_pages">pagina\\'s</string>
    <string name="progress_percent">percentage</string>
    <string name="progress_post_to_feed">In feed plaatsen</string>
    <string name="progress_comment_label">Reactie:</string>
    <string name="progress_comment_hint">Schrijf een reactie…</string>
    <string name="progress_spoiler_alert">Spoilerwaarschuwing toevoegen</string>
    <string name="progress_spoiler_hint">Spoilertekst…</string>
    <string name="progress_privacy_public">Openbaar</string>
    <string name="progress_privacy_followers">Volgers</string>
    <string name="progress_privacy_private">Privé</string>
    <string name="progress_btn_share">Delen</string>
    <string name="progress_btn_save">Opslaan</string>
    <string name="progress_btn_cancel">Annuleren</string>
    <string name="progress_success">Status bijgewerkt</string>
    <string name="progress_error">Fout: %1$s</string>
    <string name="progress_network_error">Netwerkfout: %1$s</string>""",
    "pl": """
    <string name="progress_dialog_title">Aktualizuj postęp</string>
    <string name="progress_label">Postęp:</string>
    <string name="progress_dialog_hint">Strona lub procent</string>
    <string name="progress_pages">stron</string>
    <string name="progress_percent">procent</string>
    <string name="progress_post_to_feed">Opublikuj w aktualnościach</string>
    <string name="progress_comment_label">Komentarz:</string>
    <string name="progress_comment_hint">Napisz komentarz…</string>
    <string name="progress_spoiler_alert">Dodaj ostrzeżenie o spoilerze</string>
    <string name="progress_spoiler_hint">Tekst spoilera…</string>
    <string name="progress_privacy_public">Publiczne</string>
    <string name="progress_privacy_followers">Obserwujący</string>
    <string name="progress_privacy_private">Prywatne</string>
    <string name="progress_btn_share">Udostępnij</string>
    <string name="progress_btn_save">Zapisz</string>
    <string name="progress_btn_cancel">Anuluj</string>
    <string name="progress_success">Status zaktualizowany</string>
    <string name="progress_error">Błąd: %1$s</string>
    <string name="progress_network_error">Błąd sieci: %1$s</string>""",
    "pt": """
    <string name="progress_dialog_title">Atualizar progresso</string>
    <string name="progress_label">Progresso:</string>
    <string name="progress_dialog_hint">Página ou porcentagem</string>
    <string name="progress_pages">páginas</string>
    <string name="progress_percent">porcentagem</string>
    <string name="progress_post_to_feed">Publicar no feed</string>
    <string name="progress_comment_label">Comentário:</string>
    <string name="progress_comment_hint">Escreva um comentário…</string>
    <string name="progress_spoiler_alert">Incluir aviso de spoiler</string>
    <string name="progress_spoiler_hint">Texto do spoiler…</string>
    <string name="progress_privacy_public">Público</string>
    <string name="progress_privacy_followers">Seguidores</string>
    <string name="progress_privacy_private">Privado</string>
    <string name="progress_btn_share">Compartilhar</string>
    <string name="progress_btn_save">Salvar</string>
    <string name="progress_btn_cancel">Cancelar</string>
    <string name="progress_success">Status atualizado</string>
    <string name="progress_error">Erro: %1$s</string>
    <string name="progress_network_error">Falha de rede: %1$s</string>""",
    "ro": """
    <string name="progress_dialog_title">Actualizare progres</string>
    <string name="progress_label">Progres:</string>
    <string name="progress_dialog_hint">Pagină sau procent</string>
    <string name="progress_pages">pagini</string>
    <string name="progress_percent">procent</string>
    <string name="progress_post_to_feed">Postează în flux</string>
    <string name="progress_comment_label">Comentariu:</string>
    <string name="progress_comment_hint">Scrie un comentariu…</string>
    <string name="progress_spoiler_alert">Includeți alertă de spoiler</string>
    <string name="progress_spoiler_hint">Text spoiler…</string>
    <string name="progress_privacy_public">Public</string>
    <string name="progress_privacy_followers">Urmăritori</string>
    <string name="progress_privacy_private">Privat</string>
    <string name="progress_btn_share">Distribuie</string>
    <string name="progress_btn_save">Salvează</string>
    <string name="progress_btn_cancel">Anulează</string>
    <string name="progress_success">Stare actualizată</string>
    <string name="progress_error">Eroare: %1$s</string>
    <string name="progress_network_error">Eroare rețea: %1$s</string>""",
    "sv": """
    <string name="progress_dialog_title">Uppdatera framsteg</string>
    <string name="progress_label">Framsteg:</string>
    <string name="progress_dialog_hint">Sida eller procent</string>
    <string name="progress_pages">sidor</string>
    <string name="progress_percent">procent</string>
    <string name="progress_post_to_feed">Publicera i flödet</string>
    <string name="progress_comment_label">Kommentar:</string>
    <string name="progress_comment_hint">Skriv en kommentar…</string>
    <string name="progress_spoiler_alert">Lägg till spoilervarning</string>
    <string name="progress_spoiler_hint">Spoilertext…</string>
    <string name="progress_privacy_public">Offentlig</string>
    <string name="progress_privacy_followers">Följare</string>
    <string name="progress_privacy_private">Privat</string>
    <string name="progress_btn_share">Dela</string>
    <string name="progress_btn_save">Spara</string>
    <string name="progress_btn_cancel">Avbryt</string>
    <string name="progress_success">Status uppdaterad</string>
    <string name="progress_error">Fel: %1$s</string>
    <string name="progress_network_error">Nätverksfel: %1$s</string>""",
    "uk": """
    <string name="progress_dialog_title">Оновити прогрес</string>
    <string name="progress_label">Прогрес:</string>
    <string name="progress_dialog_hint">Сторінка або відсоток</string>
    <string name="progress_pages">сторінок</string>
    <string name="progress_percent">відсоток</string>
    <string name="progress_post_to_feed">Опублікувати в стрічці</string>
    <string name="progress_comment_label">Коментар:</string>
    <string name="progress_comment_hint">Напишіть коментар…</string>
    <string name="progress_spoiler_alert">Додати попередження про спойлер</string>
    <string name="progress_spoiler_hint">Текст спойлера…</string>
    <string name="progress_privacy_public">Публічно</string>
    <string name="progress_privacy_followers">Читачі</string>
    <string name="progress_privacy_private">Приватно</string>
    <string name="progress_btn_share">Поділитися</string>
    <string name="progress_btn_save">Зберегти</string>
    <string name="progress_btn_cancel">Скасувати</string>
    <string name="progress_success">Статус оновлено</string>
    <string name="progress_error">Помилка: %1$s</string>
    <string name="progress_network_error">Помилка мережі: %1$s</string>"""
}

# The regex searches for the old progress strings (from progress_dialog_title to progress_network_error)
# and replaces them with the new translated block.
pattern = re.compile(
    r'(\s*<string name="progress_dialog_title">.*?</string>\s*<string name="progress_dialog_hint">.*?</string>\s*<string name="progress_btn_save">.*?</string>\s*<string name="progress_btn_cancel">.*?</string>\s*<string name="progress_success">.*?</string>\s*<string name="progress_error">.*?</string>\s*<string name="progress_network_error">.*?</string>\n)',
    re.DOTALL
)

base_dir = "app/src/main/res"
for lang, replacement in languages.items():
    lang_dir = os.path.join(base_dir, f"values-{lang}")
    file_path = os.path.join(lang_dir, "strings.xml")
    if os.path.exists(file_path):
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
            
        new_content = pattern.sub(replacement + "\n", content)
        if new_content != content:
            with open(file_path, "w", encoding="utf-8") as f:
                f.write(new_content)
            print(f"Updated {file_path}")
        else:
            print(f"No match found or already updated in {file_path}")
    else:
        print(f"File not found: {file_path}")

