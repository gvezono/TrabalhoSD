# TrabalhoSD
Repositório destinado à desenvolvimento e apresentação da matéria Sistemas Distribuídos.

# Entrega 0
Formalização do projeto: o mesmo consiste em criar um servidor local de empresa com departamento de licitações, já que, nessa área geram muitos documentos. Sendo assim, é interessante ter um servidor para armazenar tantos itens que provavelmente não serão mais acessados, porém, precisam ser armazenados pela empresa por possíveis futuros motivos de consulta.

Testes:
  - Teste de concorrência: demonstrando que múltiplos funcionários podem podem enviar/acessar arquivos ao mesmo tempo, sem comportamentos estranhos.
  - Demonstração de funcionalidades: conseguir enviar/acessar arquivos sem erros, mostrar o impedimento de acesso ao servidor com base no horário.

# Entrega 1
Requisitos:
  - Java e JDK >=7
  - Apache Ant >=1.8

1. Clone o projeto:
```
git clone https://github.com/gvezono/TrabalhoSD.git
```

2. Entre na pasta do projeto e compile:

```
cd TrabalhoSD
cd Servidor && ant jar && cd ..
cd Cliente && ant jar && cd ..
```

3. Executando:
  - Servidor:
```
java -jar Servidor/dist/Servidor.jar
```
  - Cliente:
```
java -jar Cliente/dist/Cliente.jar
```
