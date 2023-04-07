from abc import ABC, abstractmethod
from typing import Tuple
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
import smtplib


class EmailSender(ABC):
    """
    An abstract base class for email senders.
    """
    @abstractmethod
    def send_email(self, sender_email: str, sender_password: str, recipient_email: str, subject: str, content: str) -> Tuple[str, str]:
        """
        Send an email.

        Args:
            sender_email (str): The email address of the sender.
            sender_password (str): The password of the sender's email address.
            recipient_email (str): The email address of the recipient.
            subject (str): The subject of the email.
            content (str): The content of the email.

        Returns:
            Tuple[str, str]: A tuple containing the status code and message of the email sent.

        """
        pass


class SmtpEmailSender(EmailSender):
    """
    An implementation of EmailSender that sends email using SMTP protocol.
    """
    def send_email(self, sender_email: str, sender_password: str, recipient_email: str, subject: str, content: str) -> Tuple[str, str]:
        """
        Sends an email using SMTP protocol.

        Args:
        - sender_email (str): The email address of the sender.
        - sender_password (str): The password of the sender's email address.
        - recipient_email (str): The email address of the recipient.
        - subject (str): The subject of the email.
        - content (str): The content of the email.

        Returns:
        - Tuple[str, str]: A tuple containing the status code and message of the email sent.

        """

        message = MIMEMultipart()
        message["From"] = sender_email
        message["To"] = recipient_email
        message["Subject"] = subject
        message.attach(MIMEText(content, "html"))

        # Send the email using SMTP
        with smtplib.SMTP("smtp.gmail.com", port=587) as smtp_server:
            smtp_server.starttls()
            smtp_server.login(sender_email, sender_password)
            smtp_server.sendmail(sender_email, recipient_email, message.as_string())

        return ("200", "Email sent successfully")


class EmailSenderFactory:
    """
    A factory class for creating EmailSender objects.
    """
    @staticmethod
    def get_email_sender(sender_type: str) -> EmailSender:
        """
        Returns an EmailSender object.

        Args:
            sender_type (str): The type of the email sender.

        Returns:
            EmailSender: An EmailSender object.

        Raises:
            ValueError: If an invalid sender type is provided.
        """
        if sender_type == "smtp":
            return SmtpEmailSender()
        else:
            raise ValueError(f"Invalid sender type: {sender_type}")
