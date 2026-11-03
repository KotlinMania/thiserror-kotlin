use std::cell::{Cell, RefCell};
use std::fmt::{self, Pointer, Write};

#[path = "../tmp/thiserror/src/var.rs"]
mod var;

#[test]
fn forwards_formatting_options_and_output_once() {
    struct Probe<'a>(&'a Cell<usize>);
    impl Pointer for Probe<'_> {
        fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
            self.0.set(self.0.get() + 1);
            assert_eq!(formatter.fill(), '🙂');
            assert_eq!(formatter.align(), Some(fmt::Alignment::Center));
            assert_eq!(formatter.width(), Some(18));
            assert_eq!(formatter.precision(), Some(3));
            assert!(formatter.sign_plus());
            assert!(!formatter.sign_minus());
            assert!(formatter.alternate());
            assert!(formatter.sign_aware_zero_pad());
            formatter.write_str("formatted value")
        }
    }
    let calls = Cell::new(0);
    let value = Probe(&calls);
    let variable = var::Var(&value);
    assert!(std::ptr::eq(variable.0, &value));
    let mut output = String::new();
    write!(output, "{:🙂^+#018.3p}", variable).unwrap();
    assert_eq!(output, "formatted value");
    assert_eq!(calls.get(), 1);
}

#[test]
fn forwards_writer_error_without_retry() {
    struct Probe;
    impl Pointer for Probe {
        fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
            formatter.write_str("value")
        }
    }
    struct Reject<'a>(&'a Cell<usize>);
    impl Write for Reject<'_> {
        fn write_str(&mut self, _: &str) -> fmt::Result {
            self.0.set(self.0.get() + 1);
            Err(fmt::Error)
        }
    }
    let calls = Cell::new(0);
    let mut writer = Reject(&calls);
    assert_eq!(write!(writer, "{:p}", var::Var(&Probe)), Err(fmt::Error));
    assert_eq!(calls.get(), 1);
}

#[test]
fn preserves_panic_and_previous_writes() {
    struct Probe;
    impl Pointer for Probe {
        fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
            formatter.write_str("first")?;
            formatter.write_str("second")
        }
    }
    struct FailAfterFirst<'a>(&'a RefCell<Vec<String>>);
    impl Write for FailAfterFirst<'_> {
        fn write_str(&mut self, value: &str) -> fmt::Result {
            self.0.borrow_mut().push(value.to_owned());
            if self.0.borrow().len() == 2 {
                std::panic::panic_any("writer failed");
            }
            Ok(())
        }
    }
    let writes = RefCell::new(Vec::new());
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        write!(FailAfterFirst(&writes), "{:p}", var::Var(&Probe))
    }));
    assert_eq!(result.unwrap_err().downcast_ref::<&str>(), Some(&"writer failed"));
    assert_eq!(*writes.borrow(), ["first", "second"]);
}

#[test]
fn writes_a_complete_unicode_scalar_once() {
    struct Writer(Vec<String>);
    impl Write for Writer {
        fn write_str(&mut self, value: &str) -> fmt::Result {
            self.0.push(value.to_owned());
            Ok(())
        }
    }
    let mut writer = Writer(Vec::new());
    writer.write_char('🙂').unwrap();
    assert_eq!(writer.0, ["🙂"]);
}
