#include "List.H"

List *create_fixed(int n) {
  List *head=0;
  List *newElement;

  newElement = new List(n);
  newElement->n = head;
  head = newElement;

  newElement = new List(n);
  newElement->n = head;
  head = newElement;

  newElement = new List(n);
  newElement->n = head;
  head = newElement;

  newElement = new List(n);
  newElement->n = head;
  head = newElement;

  newElement = new List(n);
  newElement->n = head;
  head = newElement;

  newElement = new List(n);
  newElement->n = head;
  head = newElement;

  return head;
}


// appends second list to tail of first
// RinetzkySagiv01: app
List* app_recur(List* p, List* q) {
  List* t;

  if (p == NULL) {
    return q;
  }

  t = p->n;
  t = app_recur(t,q);

  p->n = t;
  return p;
}

int main(int argc, char **argv) {
  List *a = create_fixed(6);
  List *b = create_fixed(6);

  List *head = app_recur(a, b);

  return 1;
}

