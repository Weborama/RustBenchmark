CREATE TABLE clients (
  id serial primary key,
  name varchar(100) not null unique
);

insert into clients (id, name) values (1, 'Uber Client');
