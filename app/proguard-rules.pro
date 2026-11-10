# Regras do projeto Banco do Pai.
# O mapeamento do Firestore é manual, então não dependemos de reflection.
# Mantemos as classes de modelo por segurança em builds futuros com minify.
-keep class com.bancodopai.domain.model.** { *; }
