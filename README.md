# TrabalhoSD
Repositório destinado à desenvolvimento e apresentação da matéria Sistemas Distribuídos.

## Alunos
Tiago Henrique  11411BSI204

Gabriel Vezono  11611BCC050

Victor Melazo   11611BCC018

Carolina Alves  11411BSI234

Matheus Araújo 11521BSI249 

# Descrição
Formalização do projeto: o mesmo consiste em criar um servidor local de empresa com departamento de licitações, já que, nessa área geram muitos documentos. Sendo assim, é interessante ter um servidor para armazenar tantos itens que provavelmente não serão mais acessados, porém, precisam ser armazenados pela empresa por possíveis futuros motivos de consulta.

Testes:
  - Teste de concorrência: demonstrando que múltiplos funcionários podem podem enviar/acessar arquivos ao mesmo tempo, sem comportamentos estranhos.
  - Demonstração de funcionalidades: conseguir enviar/acessar arquivos sem erros, mostrar o impedimento de acesso ao servidor com base no horário.

# Procedimentos
### Requisitos
Java JDK == 8
```
sudo apt install openjdk-8-jdk
```
Apache Maven >= 3.0
```
sudo apt install maven
```

### Instalando
Clone o projeto e entre na pasta do projeto
```
git clone https://github.com/gvezono/TrabalhoSD.git
cd TrabalhoSD
```

Compile usando o mvn

  - Servidor
```
cd Servidor && mvn install && cd ..
```
  - Cliente
```
cd Cliente && mvn install && cd ..
```

### Executando
  - Servidor
```
java -jar Servidor/target/Servidor-1.0-jar-with-dependencies.jar 0 127.0.0.1 50052 127.0.0.1 50053 127.0.0.1 50054
```
  - Cliente
```
java -jar Cliente/target/Cliente-1.0-jar-with-dependencies.jar
```
