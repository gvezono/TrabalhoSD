# TrabalhoSD
Repositório destinado à desenvolvimento e apresentação da matéria Sistemas Distribuídos.

## Alunos
Tiago Henrique  11411BSI204

Gabriel Vezono  11611BCC050

Victor Melazo   11611BCC028

Carolina Alves  11411BSI234

# Entrega 0
Formalização do projeto: o mesmo consiste em criar um servidor local de empresa com departamento de licitações, já que, nessa área geram muitos documentos. Sendo assim, é interessante ter um servidor para armazenar tantos itens que provavelmente não serão mais acessados, porém, precisam ser armazenados pela empresa por possíveis futuros motivos de consulta.

Testes:
  - Teste de concorrência: demonstrando que múltiplos funcionários podem podem enviar/acessar arquivos ao mesmo tempo, sem comportamentos estranhos.
  - Demonstração de funcionalidades: conseguir enviar/acessar arquivos sem erros, mostrar o impedimento de acesso ao servidor com base no horário.

# Entrega 1
### Requisitos
Java JDK >= 7
```
sudo apt-get install default-jre
sudo apt-get install default-jdk
```
Apache Ant >= 1.8
```
sudo apt-get install ant
```

### Instalando
Clone o projeto e entre na pasta do projeto
```
git clone https://github.com/gvezono/TrabalhoSD.git
cd TrabalhoSD
```

Compile usando o ant

  - Servidor
```
cd Servidor && ant jar && cd ..
```
  - Cliente
```
cd Cliente && ant jar && cd ..
```

### Executando
  - Servidor

Sem restrição de horário
```
java -jar Servidor/dist/Servidor.jar
```
Com restrição de horário - pode ser acessado das 8h às 18h
```
java -jar Servidor/dist/Servidor.jar restricao
```
  - Cliente
```
java -jar Cliente/dist/Cliente.jar
```
